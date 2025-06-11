package io.eddayy.flutter_star_prnt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.webkit.URLUtil
import com.starmicronics.stario.PortInfo
import com.starmicronics.stario.StarIOPort
import com.starmicronics.stario.StarPrinterStatus
import com.starmicronics.starioextension.ICommandBuilder
import com.starmicronics.starioextension.ICommandBuilder.AlignmentPosition
import com.starmicronics.starioextension.ICommandBuilder.BarcodeSymbology
import com.starmicronics.starioextension.ICommandBuilder.BarcodeWidth
import com.starmicronics.starioextension.ICommandBuilder.BlackMarkType
import com.starmicronics.starioextension.ICommandBuilder.CodePageType
import com.starmicronics.starioextension.ICommandBuilder.CutPaperAction
import com.starmicronics.starioextension.ICommandBuilder.InternationalType
import com.starmicronics.starioextension.ICommandBuilder.LogoSize
import com.starmicronics.starioextension.ICommandBuilder.PeripheralChannel
import com.starmicronics.starioextension.StarIoExt
import com.starmicronics.starioextension.StarIoExt.Emulation
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

@Suppress("DEPRECATION")
/** FlutterStarPrntPlugin */
class FlutterStarPrntPlugin : FlutterPlugin, MethodCallHandler {

    private lateinit var applicationContext: Context

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(binding.binaryMessenger, "flutter_star_prnt")
        channel.setMethodCallHandler(FlutterStarPrntPlugin())
        applicationContext = binding.applicationContext
    }
    override fun onMethodCall(call: MethodCall, rawResult: Result) {
        val result = MethodResultWrapper(rawResult)
        Thread(MethodRunner(call, result)).start()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {}
    inner class MethodRunner(private val call: MethodCall, private val result: Result) : Runnable {

        override fun run() {
            when (call.method) {
                "portDiscovery" -> {
                    portDiscovery(call, result)
                }
                "checkStatus" -> {
                    checkStatus(call, result)
                }
                "print" -> {
                    print(call, result)
                }
                else -> result.notImplemented()
            }
        }
    }
    class MethodResultWrapper(private val methodResult: Result) : Result {

        private val handler: Handler = Handler(Looper.getMainLooper())

        override fun success(result: Any?) {
            handler.post { methodResult.success(result) }
        }

        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
            handler.post { methodResult.error(errorCode, errorMessage, errorDetails) }
        }

        override fun notImplemented() {
            handler.post { methodResult.notImplemented() }
        }
    }
    fun portDiscovery(call: MethodCall, result: Result) {
        val strInterface: String = call.argument<String>("type") as String
        val response: MutableList<Map<String, String>>
        try {
            response = when (strInterface) {
                "LAN" -> {
                    getPortDiscovery("LAN")
                }
                "Bluetooth" -> {
                    getPortDiscovery("Bluetooth")
                }
                "USB" -> {
                    getPortDiscovery("USB")
                }
                else -> {
                    getPortDiscovery("All")
                }
            }
            result.success(response)
        } catch (e: Exception) {
            result.error("PORT_DISCOVERY_ERROR", e.message, null)
        }
    }
    fun checkStatus(call: MethodCall, result: Result) {
        val portName: String = call.argument<String>("portName") as String
        val emulation: String = call.argument<String>("emulation") as String

        var port: StarIOPort? = null
        try {
            val portSettings: String = getPortSettingsOption(emulation)

            port = StarIOPort.getPort(portName, portSettings, 10000, applicationContext)

            // A sleep is used to get time for the socket to completely open
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {}

            val status: StarPrinterStatus = port.retreiveStatus()


            val json: MutableMap<String, Any?> = mutableMapOf()
            json["is_success"] = true
            json["offline"] = status.offline
            json["coverOpen"] = status.coverOpen
            json["overTemp"] = status.overTemp
            json["cutterError"] = status.cutterError
            json["receiptPaperEmpty"] = status.receiptPaperEmpty
            try {
                val firmwareInformationMap: Map<String, String> = port.firmwareInformation
                json["ModelName"] = firmwareInformationMap["ModelName"]
                json["FirmwareVersion"] = firmwareInformationMap["FirmwareVersion"]
            }catch (e: Exception) {
                json["error_message"] = e.message
            }
            result.success(json)
        } catch (e: Exception) {
            result.error("CHECK_STATUS_ERROR", e.message, null)
        } finally {
            if (port != null) {
                try {
                    StarIOPort.releasePort(port)
                } catch (e: Exception) {
                    result.error("CHECK_STATUS_ERROR", e.message, null)
                }
            }
        }
    }

    fun print(call: MethodCall, result: Result) {
        val portName: String = call.argument<String>("portName") as String
        val emulation: String = call.argument<String>("emulation") as String
        val printCommands: ArrayList<Map<String, Any>> =
            call.argument<ArrayList<Map<String, Any>>>("printCommands") as ArrayList<Map<String, Any>>
        if (printCommands.size < 1) {
            val json: MutableMap<String, Any?> = mutableMapOf()

            json["offline"] = false
            json["coverOpen"] = false
            json["cutterError"] = false
            json["receiptPaperEmpty"] = false
            json["info_message"] = "No dat to print"
            json["is_success"] = true
            result.success(json)
            return
        }
        val builder: ICommandBuilder = StarIoExt.createCommandBuilder(getEmulation(emulation))
        builder.beginDocument()
        appendCommands(builder, printCommands, applicationContext)
        builder.endDocument()
        sendCommand(
            portName,
            getPortSettingsOption(emulation),
            builder.commands,
            result)
    }

    private fun getPortDiscovery(interfaceName: String): MutableList<Map<String, String>> {
        val arrayDiscovery: MutableList<PortInfo> = mutableListOf()
        val arrayPorts: MutableList<Map<String, String>> = mutableListOf()

        if (interfaceName == "Bluetooth" || interfaceName == "All") {
            for (portInfo in StarIOPort.searchPrinter("BT:")) {
                arrayDiscovery.add(portInfo)
            }
        }
        if (interfaceName == "LAN" || interfaceName == "All") {
            for (port in StarIOPort.searchPrinter("TCP:")) {
                arrayDiscovery.add(port)
            }
        }
        if (interfaceName == "USB" || interfaceName == "All") {
            try {
                for (port in StarIOPort.searchPrinter("USB:", applicationContext)) {
                    arrayDiscovery.add(port)
                }
            } catch (e: Exception) {
                Log.e("FlutterStarPrnt", "usb not connected", e)
            }
        }
        for (discovery in arrayDiscovery) {
            val port: MutableMap<String, String> = mutableMapOf()

            if (discovery.portName.startsWith("BT:"))
                port["portName"] = "BT:" + discovery.macAddress
            else port["portName"] = discovery.portName

            if (!discovery.macAddress.equals("")) {

                port["macAddress"] = discovery.macAddress

                if (discovery.portName.startsWith("BT:")) {
                    port["modelName"] = discovery.portName
                } else if (!discovery.modelName.equals("")) {
                    port["modelName"] = discovery.modelName
                }
            } else if (interfaceName == "USB" || interfaceName == "All") {
                if (!discovery.modelName.equals("")) {
                    port["modelName"] = discovery.modelName
                }
                if (!discovery.usbSerialNumber.equals(" SN:")) {
                    port["USBSerialNumber"] = discovery.usbSerialNumber
                }
            }

            arrayPorts.add(port)
        }

        return arrayPorts
    }

    private fun getPortSettingsOption(
        emulation: String
    ): String { // generate the port settings depending on the emulation type
        return when (emulation) {
            "EscPosMobile" -> "mini"
            "EscPos" -> "escpos"
            "StarPRNT", "StarPRNTL" -> "Portable;l"
            else -> emulation
        }
    }
    private fun getEmulation(emulation: String?): Emulation {
        return when (emulation) {
            "StarPRNT" -> Emulation.StarPRNT
            "StarPRNTL" -> Emulation.StarPRNTL
            "StarLine" -> Emulation.StarLine
            "StarGraphic" -> Emulation.StarGraphic
            "EscPos" -> Emulation.EscPos
            "EscPosMobile" -> Emulation.EscPosMobile
            "StarDotImpact" -> Emulation.StarDotImpact
            else -> Emulation.StarLine
        }
    }

    private fun appendCommands(
        builder: ICommandBuilder,
        printCommands: ArrayList<Map<String, Any>>?,
        context: Context
    ) {
        var encoding: Charset = Charset.forName("US-ASCII")

        printCommands?.forEach {
            if (it.containsKey("appendCharacterSpace"))
                builder.appendCharacterSpace((it["appendCharacterSpace"].toString()).toInt())
            else if (it.containsKey("appendEncoding"))
                encoding = getEncoding(it["appendEncoding"].toString())
            else if (it.containsKey("appendCodePage"))
                builder.appendCodePage(getCodePageType(it["appendCodePage"].toString()))
            else if (it.containsKey("append"))
                builder.append(it["append"].toString().toByteArray(encoding))
            else if (it.containsKey("appendRaw"))
                builder.append(it["appendRaw"].toString().toByteArray(encoding))
            else if (it.containsKey("appendMultiple"))
                builder.appendMultiple(it["appendMultiple"].toString().toByteArray(encoding), 2, 2)
            else if (it.containsKey("appendEmphasis"))
                builder.appendEmphasis(it["appendEmphasis"].toString().toByteArray(encoding))
            else if (it.containsKey("enableEmphasis"))
                builder.appendEmphasis((it["enableEmphasis"].toString()).toBoolean())
            else if (it.containsKey("appendInvert"))
                builder.appendInvert(it["appendInvert"].toString().toByteArray(encoding))
            else if (it.containsKey("enableInvert"))
                builder.appendInvert((it["enableInvert"].toString()).toBoolean())
            else if (it.containsKey("appendUnderline"))
                builder.appendUnderLine(it["appendUnderline"].toString().toByteArray(encoding))
            else if (it.containsKey("enableUnderline"))
                builder.appendUnderLine((it["enableUnderline"].toString()).toBoolean())
            else if (it.containsKey("appendInternational"))
                builder.appendInternational(getInternational(it["appendInternational"].toString()))
            else if (it.containsKey("appendLineFeed"))
                builder.appendLineFeed((it["appendLineFeed"] as Int))
            else if (it.containsKey("appendUnitFeed"))
                builder.appendUnitFeed((it["appendUnitFeed"] as Int))
            else if (it.containsKey("appendLineSpace"))
                builder.appendLineSpace((it["appendLineSpace"] as Int))
            else if (it.containsKey("appendFontStyle"))
                builder.appendFontStyle((getFontStyle(it["appendFontStyle"] as String)))
            else if (it.containsKey("appendCutPaper"))
                builder.appendCutPaper(getCutPaperAction(it["appendCutPaper"].toString()))
            else if (it.containsKey("openCashDrawer"))
                builder.appendPeripheral(getPeripheralChannel(it["openCashDrawer"] as Int))
            else if (it.containsKey("appendBlackMark"))
                builder.appendBlackMark(getBlackMarkType(it["appendBlackMark"].toString()))
            else if (it.containsKey("appendBytes"))
                builder.append(
                    it["appendBytes"]
                        .toString()
                        .toByteArray(encoding)) // TODO: test this in the future
            else if (it.containsKey("appendRawBytes"))
                builder.appendRaw(
                    it["appendRawBytes"]
                        .toString()
                        .toByteArray(encoding)) // TODO: test this in the future
            else if (it.containsKey("appendAbsolutePosition")) {
                if (it.containsKey("data"))
                    builder.appendAbsolutePosition(
                        (it["data"].toString().toByteArray(encoding)),
                        (it["appendAbsolutePosition"].toString()).toInt())
                else builder.appendAbsolutePosition((it["appendAbsolutePosition"].toString()).toInt())
            } else if (it.containsKey("appendAlignment")) {
                if (it.containsKey("data"))
                    builder.appendAlignment(
                        (it["data"].toString().toByteArray(encoding)),
                        getAlignment(it["appendAlignment"].toString()))
                else builder.appendAlignment(getAlignment(it["appendAlignment"].toString()))
            } else if (it.containsKey("appendHorizontalTabPosition"))
                builder.appendHorizontalTabPosition(
                    it["appendHorizontalTabPosition"] as IntArray) // TODO: test this in the future
            else if (it.containsKey("appendLogo")) {
                if (it.containsKey("logoSize"))
                    builder.appendLogo(
                        getLogoSize(it["logoSize"] as String), it["appendLogo"] as Int)
                else builder.appendLogo(getLogoSize("Normal"), it["appendLogo"] as Int)
            } else if (it.containsKey("appendBarcode")) {
                val barcodeSymbology: BarcodeSymbology =
                    if (it.containsKey("BarcodeSymbology"))
                        getBarcodeSymbology(it["BarcodeSymbology"].toString())
                    else getBarcodeSymbology("Code128")
                val barcodeWidth: BarcodeWidth =
                    if (it.containsKey("BarcodeWidth")) getBarcodeWidth(it["BarcodeWidth"].toString())
                    else getBarcodeWidth("Mode2")
                val height: Int =
                    if (it.containsKey("height")) (it["height"].toString()).toInt() else 40
                val hri: Boolean =
                    if (it.containsKey("hri")) (it["hri"].toString()).toBoolean() else true

                if (it.containsKey("absolutePosition")) {
                    builder.appendBarcodeWithAbsolutePosition(
                        it["appendBarcode"].toString().toByteArray(encoding),
                        barcodeSymbology,
                        barcodeWidth,
                        height,
                        hri,
                        it["absolutePosition"] as Int)
                } else if (it.containsKey("alignment")) {
                    builder.appendBarcodeWithAlignment(
                        it["appendBarcode"].toString().toByteArray(encoding),
                        barcodeSymbology,
                        barcodeWidth,
                        height,
                        hri,
                        getAlignment(it["alignment"].toString()))
                } else
                    builder.appendBarcode(
                        it["appendBarcode"].toString().toByteArray(encoding),
                        barcodeSymbology,
                        barcodeWidth,
                        height,
                        hri)
            } else if (it.containsKey("appendBitmap")) {
                val diffusion: Boolean =
                    if (it.containsKey("diffusion")) (it["diffusion"].toString()).toBoolean() else true
                val width: Int = if (it.containsKey("width")) (it["width"].toString()).toInt() else 576
                val bothScale: Boolean =
                    if (it.containsKey("bothScale")) (it["bothScale"].toString()).toBoolean() else true
                val rotation: ICommandBuilder.BitmapConverterRotation =
                    if (it.containsKey("rotation")) getConverterRotation(it["rotation"].toString())
                    else getConverterRotation("Normal")
                try {
                    val bitmap: Bitmap?
                    if (URLUtil.isValidUrl(it["appendBitmap"].toString())) {
                        val imageUri: Uri = Uri.parse(it["appendBitmap"].toString())
                        bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    } else {
                        bitmap = BitmapFactory.decodeFile(it["appendBitmap"].toString())
                    }

                    if (bitmap != null) {
                        if (it.containsKey("absolutePosition")) {
                            builder.appendBitmapWithAbsolutePosition(
                                bitmap,
                                diffusion,
                                width,
                                bothScale,
                                rotation,
                                (it["absolutePosition"].toString()).toInt())
                        } else if (it.containsKey("alignment")) {
                            builder.appendBitmapWithAlignment(
                                bitmap,
                                diffusion,
                                width,
                                bothScale,
                                rotation,
                                getAlignment(it["alignment"].toString()))
                        } else builder.appendBitmap(bitmap, diffusion, width, bothScale, rotation)
                    }
                } catch (e: Exception) {
                    Log.e("FlutterStarPrnt", "append bitmap failed", e)
                }
            } else if (it.containsKey("appendBitmapText")) {
                val fontSize: Float =
                    if (it.containsKey("fontSize")) (it["fontSize"].toString()).toFloat()
                    else 25.toFloat()
                val diffusion: Boolean =
                    if (it.containsKey("diffusion")) (it["diffusion"].toString()).toBoolean() else true
                val width: Int = if (it.containsKey("width")) (it["width"].toString()).toInt() else 576
                val bothScale: Boolean =
                    if (it.containsKey("bothScale")) (it["bothScale"].toString()).toBoolean() else true
                val text: String = it["appendBitmapText"].toString()
                val typeface: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                val bitmap: Bitmap = createBitmapFromText(text, fontSize, width, typeface)
                val rotation: ICommandBuilder.BitmapConverterRotation =
                    if (it.containsKey("rotation")) getConverterRotation(it["rotation"].toString())
                    else getConverterRotation("Normal")
                if (it.containsKey("absolutePosition")) {
                    builder.appendBitmapWithAbsolutePosition(
                        bitmap, diffusion, width, bothScale, rotation, it["absolutePosition"] as Int)
                } else if (it.containsKey("alignment")) {
                    builder.appendBitmapWithAlignment(
                        bitmap,
                        diffusion,
                        width,
                        bothScale,
                        rotation,
                        getAlignment(it["alignment"].toString()))
                } else builder.appendBitmap(bitmap, diffusion, width, bothScale, rotation)
            } else if (it.containsKey("appendBitmapByteArray")) {
                val diffusion: Boolean = if (it.containsKey("diffusion")) (it["diffusion"].toString()).toBoolean() else true
                val width: Int = if (it.containsKey("width")) (it["width"].toString()).toInt() else 576
                val bothScale: Boolean = if (it.containsKey("bothScale")) (it["bothScale"].toString()).toBoolean() else true
                val rotation: ICommandBuilder.BitmapConverterRotation = if (it.containsKey("rotation")) getConverterRotation(it["rotation"].toString()) else getConverterRotation("Normal")
                try {
                    val byteArray: ByteArray = it["appendBitmapByteArray"] as ByteArray
                    val bitmap: Bitmap? = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    if (bitmap != null) {
                        if (it.containsKey("absolutePosition")) {
                            builder.appendBitmapWithAbsolutePosition(bitmap, diffusion, width, bothScale, rotation, (it["absolutePosition"].toString()).toInt())
                        } else if (it.containsKey("alignment")) {
                            builder.appendBitmapWithAlignment(bitmap, diffusion, width, bothScale, rotation, getAlignment(it["alignment"].toString()))
                        } else builder.appendBitmap(bitmap, diffusion, width, bothScale, rotation)
                    }
                } catch (e: Exception) {
                    Log.e("FlutterStarPrnt", "appendectomyArray failed", e)
                }}
        }
    }

    private fun getEncoding(encoding: String): Charset {
        when (encoding) {
            "US-ASCII" -> return Charset.forName("US-ASCII") // English
            "Windows-1252" -> {
                return try {
                    Charset.forName("Windows-1252") // French, German, Portuguese, Spanish
                } catch (e: UnsupportedCharsetException) { // not supported using UTF-8 Instead
                    Charset.forName("UTF-8")
                }
            }
            "Shift-JIS" -> {
                return try {
                    Charset.forName("Shift-JIS") // Japanese
                } catch (e: UnsupportedCharsetException) { // not supported using UTF-8 Instead
                    Charset.forName("UTF-8")
                }
            }
            "Windows-1251" -> {
                return try {
                    Charset.forName("Windows-1251") // Russian
                } catch (e: UnsupportedCharsetException) { // not supported using UTF-8 Instead
                    Charset.forName("UTF-8")
                }
            }
            "GB2312" -> {
                return try {
                    Charset.forName("GB2312") // Simplified Chinese
                } catch (e: UnsupportedCharsetException) { // not supported using UTF-8 Instead
                    Charset.forName("UTF-8")
                }
            }
            "Big5" -> {
                return try {
                    Charset.forName("Big5") // Traditional Chinese
                } catch (e: UnsupportedCharsetException) { // not supported using UTF-8 Instead
                    Charset.forName("UTF-8")
                }
            }
            "UTF-8" -> return Charset.forName("UTF-8") // UTF-8
            else -> return Charset.forName("US-ASCII")
        }
    }
    private fun getCodePageType(codePageType: String): CodePageType {
        when (codePageType) {
            "CP437" -> return CodePageType.CP437
            "CP737" -> return CodePageType.CP737
            "CP772" -> return CodePageType.CP772
            "CP774" -> return CodePageType.CP774
            "CP851" -> return CodePageType.CP851
            "CP852" -> return CodePageType.CP852
            "CP855" -> return CodePageType.CP855
            "CP857" -> return CodePageType.CP857
            "CP858" -> return CodePageType.CP858
            "CP860" -> return CodePageType.CP860
            "CP861" -> return CodePageType.CP861
            "CP862" -> return CodePageType.CP862
            "CP863" -> return CodePageType.CP863
            "CP864" -> return CodePageType.CP864
            "CP865" -> return CodePageType.CP866
            "CP869" -> return CodePageType.CP869
            "CP874" -> return CodePageType.CP874
            "CP928" -> return CodePageType.CP928
            "CP932" -> return CodePageType.CP932
            "CP999" -> return CodePageType.CP999
            "CP1001" -> return CodePageType.CP1001
            "CP1250" -> return CodePageType.CP1250
            "CP1251" -> return CodePageType.CP1251
            "CP1252" -> return CodePageType.CP1252
            "CP2001" -> return CodePageType.CP2001
            "CP3001" -> return CodePageType.CP3001
            "CP3002" -> return CodePageType.CP3002
            "CP3011" -> return CodePageType.CP3011
            "CP3012" -> return CodePageType.CP3012
            "CP3021" -> return CodePageType.CP3021
            "CP3041" -> return CodePageType.CP3041
            "CP3840" -> return CodePageType.CP3840
            "CP3841" -> return CodePageType.CP3841
            "CP3843" -> return CodePageType.CP3843
            "CP3845" -> return CodePageType.CP3845
            "CP3846" -> return CodePageType.CP3846
            "CP3847" -> return CodePageType.CP3847
            "CP3848" -> return CodePageType.CP3848
            "UTF8" -> return CodePageType.UTF8
            "Blank" -> return CodePageType.Blank
            else -> return CodePageType.CP998
        }
    }

    // ICommandBuilder Constant Functions
    private fun getInternational(international: String): InternationalType {
        when (international) {
            "UK" -> return InternationalType.UK
            "USA" -> return InternationalType.USA
            "France" -> return InternationalType.France
            "Germany" -> return InternationalType.Germany
            "Denmark" -> return InternationalType.Denmark
            "Sweden" -> return InternationalType.Sweden
            "Italy" -> return InternationalType.Italy
            "Spain" -> return InternationalType.Spain
            "Japan" -> return InternationalType.Japan
            "Norway" -> return InternationalType.Norway
            "Denmark2" -> return InternationalType.Denmark2
            "Spain2" -> return InternationalType.Spain2
            "LatinAmerica" -> return InternationalType.LatinAmerica
            "Korea" -> return InternationalType.Korea
            "Ireland" -> return InternationalType.Ireland
            "Legal" -> return InternationalType.Legal
            else -> return InternationalType.USA
        }
    }

    private fun getFontStyle(fontStyle: String): ICommandBuilder.FontStyleType {
        if (fontStyle == "A") return ICommandBuilder.FontStyleType.A
        if (fontStyle == "B") return ICommandBuilder.FontStyleType.B
        return ICommandBuilder.FontStyleType.A
    }
    private fun getCutPaperAction(cutPaperAction: String): CutPaperAction {
        return when (cutPaperAction) {
            "FullCut" -> CutPaperAction.FullCut
            "FullCutWithFeed" -> CutPaperAction.FullCutWithFeed
            "PartialCut" -> CutPaperAction.PartialCut
            "PartialCutWithFeed" -> CutPaperAction.PartialCutWithFeed
            else -> CutPaperAction.PartialCutWithFeed
        }
    }
    private fun getPeripheralChannel(peripheralChannel: Int): PeripheralChannel {
        return when (peripheralChannel) {
            1 -> PeripheralChannel.No1
            2 -> PeripheralChannel.No2
            else -> PeripheralChannel.No1
        }
    }
    private fun getBlackMarkType(blackMarkType: String): BlackMarkType {
        return when (blackMarkType) {
            "Valid" -> BlackMarkType.Valid
            "Invalid" -> BlackMarkType.Invalid
            "ValidWithDetection" -> BlackMarkType.ValidWithDetection
            else -> BlackMarkType.Valid
        }
    }
    private fun getAlignment(alignment: String): AlignmentPosition {
        return when (alignment) {
            "Left" -> AlignmentPosition.Left
            "Center" -> AlignmentPosition.Center
            "Right" -> AlignmentPosition.Right
            else -> AlignmentPosition.Left
        }
    }
    private fun getLogoSize(logoSize: String): LogoSize {
        return when (logoSize) {
            "Normal" -> LogoSize.Normal
            "DoubleWidth" -> LogoSize.DoubleWidth
            "DoubleHeight" -> LogoSize.DoubleHeight
            "DoubleWidthDoubleHeight" -> LogoSize.DoubleWidthDoubleHeight
            else -> LogoSize.Normal
        }
    }
    private fun getBarcodeSymbology(barcodeSymbology: String): BarcodeSymbology {
        return when (barcodeSymbology) {
            "Code128" -> BarcodeSymbology.Code128
            "Code39" -> BarcodeSymbology.Code39
            "Code93" -> BarcodeSymbology.Code93
            "ITF" -> BarcodeSymbology.ITF
            "JAN8" -> BarcodeSymbology.JAN8
            "JAN13" -> BarcodeSymbology.JAN13
            "NW7" -> BarcodeSymbology.NW7
            "UPCA" -> BarcodeSymbology.UPCA
            "UPCE" -> BarcodeSymbology.UPCE
            else -> BarcodeSymbology.Code128
        }
    }
    private fun getBarcodeWidth(barcodeWidth: String): BarcodeWidth {
        if (barcodeWidth == "Mode1") return BarcodeWidth.Mode1
        if (barcodeWidth == "Mode2") return BarcodeWidth.Mode2
        if (barcodeWidth == "Mode3") return BarcodeWidth.Mode3
        if (barcodeWidth == "Mode4") return BarcodeWidth.Mode4
        if (barcodeWidth == "Mode5") return BarcodeWidth.Mode5
        if (barcodeWidth == "Mode6") return BarcodeWidth.Mode6
        if (barcodeWidth == "Mode7") return BarcodeWidth.Mode7
        if (barcodeWidth == "Mode8") return BarcodeWidth.Mode8
        if (barcodeWidth == "Mode9") return BarcodeWidth.Mode9
        return BarcodeWidth.Mode2
    }
    private fun getConverterRotation(
        converterRotation: String
    ): ICommandBuilder.BitmapConverterRotation {
        return when (converterRotation) {
            "Normal" -> ICommandBuilder.BitmapConverterRotation.Normal
            "Left90" -> ICommandBuilder.BitmapConverterRotation.Left90
            "Right90" -> ICommandBuilder.BitmapConverterRotation.Right90
            "Rotate180" -> ICommandBuilder.BitmapConverterRotation.Rotate180
            else -> ICommandBuilder.BitmapConverterRotation.Normal
        }
    }
    private fun createBitmapFromText(
        printText: String,
        textSize: Float,
        printWidth: Int,
        typeface: Typeface
    ): Bitmap {
        val paint = Paint()
        paint.textSize = textSize
        paint.setTypeface(typeface)
        paint.getTextBounds(printText, 0, printText.length, Rect())

        val textPaint = TextPaint(paint)
        val staticLayout =
            StaticLayout(
                printText,
                textPaint,
                printWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.toFloat(),
                0.toFloat(),
                false)

        // Create bitmap
        val bitmap: Bitmap =
            Bitmap.createBitmap(
                staticLayout.width, staticLayout.height, Bitmap.Config.ARGB_8888)

        // Create canvas
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.translate(0.toFloat(), 0.toFloat())
        staticLayout.draw(canvas)
        return bitmap
    }
    private fun sendCommand(
        portName: String,
        portSettings: String,
        commands: ByteArray,
        result: Result
    ) {
        var port: StarIOPort? = null
        var errorPosSting = ""
        try {
            port = StarIOPort.getPort(portName, portSettings, 10000, applicationContext)
            errorPosSting += "Port Opened,"
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {}
            var status: StarPrinterStatus = port.beginCheckedBlock()
            val json: MutableMap<String, Any?> = mutableMapOf()
            errorPosSting += "got status for begin Check,"
            json["offline"] = status.offline
            json["coverOpen"] = status.coverOpen
            json["cutterError"] = status.cutterError
            json["receiptPaperEmpty"] = status.receiptPaperEmpty
            var isSuccess = true
            if (status.offline) {
                json["error_message"] = "A printer is offline"
                isSuccess = false
            } else if (status.coverOpen) {
                json["error_message"] = "Printer cover is open"
                isSuccess = false
            } else if (status.receiptPaperEmpty) {
                json["error_message"] = "Paper empty"
                isSuccess = false
            } else if (status.presenterPaperJamError) {
                json["error_message"] = "Paper Jam"
                isSuccess = false
            }

            if (status.receiptPaperNearEmptyInner || status.receiptPaperNearEmptyOuter) {
                json["error_message"] = "Paper near empty"
            }
            if (isSuccess) {
                errorPosSting += "Writing to port,"
                port.writePort(commands, 0, commands.size)
                errorPosSting += "setting delay End check bock,"
                port.setEndCheckedBlockTimeoutMillis(30000) // Change the timeout time of endCheckedBlock method.
                errorPosSting += "doing End check bock,"
                try {
                    status = port.endCheckedBlock()
                }catch (e:Exception){
                    errorPosSting += "End check bock exception $e,"
                }

                json["offline"] = status.offline
                json["coverOpen"] = status.coverOpen
                json["cutterError"] = status.cutterError
                json["receiptPaperEmpty"] = status.receiptPaperEmpty
                if (status.offline) {
                    json["error_message"] = "A printer is offline"
                    isSuccess = false
                } else if (status.coverOpen) {
                    json["error_message"] = "Printer cover is open"
                    isSuccess = false
                } else if (status.receiptPaperEmpty) {
                    json["error_message"] = "Paper empty"
                    isSuccess = false
                } else if (status.presenterPaperJamError) {
                    json["error_message"] = "Paper Jam"
                    isSuccess = false
                }
            }
            json["is_success"] = isSuccess
            result.success(json)
        } catch (e: Exception) {
            result.error("STARIO_PORT_EXCEPTION", e.message + " Failed After $errorPosSting", null)
        } finally {
            if (port != null) {
                try {
                    StarIOPort.releasePort(port)
                } catch (e: Exception) {
                    // not calling error because error or status is already called from try or catch.. ignoring this exception now
//            result.error("PRINT_ERROR", e.message, null)
                }
            }
        }
    }
}
