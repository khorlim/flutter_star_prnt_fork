# 🖼️ Image Printing Examples

This guide shows how to print images using the fixed remote URL loading functionality.

## 🔧 **Issues Fixed:**

1. ✅ **Android**: Fixed `FileNotFoundException` when loading HTTPS URLs by implementing proper HTTP download
2. ✅ **iOS**: Fixed null pointer issues with proper image validation
3. ✅ **Both**: Added better error handling and logging

## 📋 **Usage Examples:**

### Remote Image URLs (Now Working!)

```dart
import 'package:flutter_star_prnt/flutter_star_prnt.dart';

PrintCommands commands = PrintCommands();

// Print image from HTTPS URL (now works on both iOS and Android)
commands.appendBitmap(
  path: "https://image.tunai.io/image/ef5668d4-b2e8-41c2-a9eb-ac7d8941f93c.png",
  width: 576,
  bothScale: true,
  alignment: StarAlignmentPosition.Center,
);

// Print image from HTTP URL
commands.appendBitmap(
  path: "http://example.com/logo.png",
  width: 400,
  bothScale: true,
);
```

### Local Images

```dart
PrintCommands commands = PrintCommands();

// Local file path
commands.appendBitmap(
  path: "/path/to/local/image.png",
  width: 576,
  bothScale: true,
);

// Content URI (Android)
commands.appendBitmap(
  path: "content://media/external/images/media/123",
  width: 576,
  bothScale: true,
);
```

### Image with Positioning

```dart
PrintCommands commands = PrintCommands();

// Centered image
commands.appendBitmap(
  path: "https://example.com/logo.png",
  alignment: StarAlignmentPosition.Center,
  width: 400,
  bothScale: true,
);

// Image at absolute position
commands.appendBitmap(
  path: "https://example.com/image.png",
  absolutePosition: 100,
  width: 300,
  bothScale: true,
);
```

### Image with Rotation

```dart
PrintCommands commands = PrintCommands();

// Rotated image
commands.appendBitmap(
  path: "https://example.com/image.png",
  rotation: StarBitmapConverterRotation.Rotate180,
  width: 576,
  bothScale: true,
);
```

### Complete Receipt with Images

```dart
Future<void> printReceiptWithLogo(PortInfo port) async {
  PrintCommands commands = PrintCommands();

  commands.appendEncoding(StarEncoding.UTF8);

  // Company logo from URL
  commands.appendBitmap(
    path: "https://yourcompany.com/logo.png",
    alignment: StarAlignmentPosition.Center,
    width: 300,
    bothScale: true,
  );

  commands.appendLineFeed(2);

  // Receipt text
  commands.appendFontStyle(StarFontStyleType.A);
  commands.appendTextWithAlignment("RECEIPT", StarAlignmentPosition.Center);
  commands.appendLineFeed();

  commands.appendFontStyle(StarFontStyleType.B);
  commands.appendText("Date: ${DateTime.now()}\n");
  commands.appendText("Order #12345\n");
  commands.appendLineFeed();

  // QR code image from URL
  commands.appendBitmap(
    path: "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=Order12345",
    alignment: StarAlignmentPosition.Center,
    width: 200,
    bothScale: true,
  );

  commands.appendCutPaper(StarCutPaperAction.PartialCutWithFeed);

  await StarPrnt.sendCommands(
    portName: port.portName!,
    emulation: StarEmulation.StarPRNT,
    printCommands: commands,
  );
}
```

### Error Handling

```dart
Future<void> printImageSafely(PortInfo port, String imageUrl) async {
  try {
    PrintCommands commands = PrintCommands();

    commands.appendBitmap(
      path: imageUrl,
      width: 576,
      bothScale: true,
    );

    var result = await StarPrnt.sendCommands(
      portName: port.portName!,
      emulation: StarEmulation.StarPRNT,
      printCommands: commands,
    );

    if (result['is_success'] == true) {
      print("Image printed successfully!");
    } else {
      print("Print failed: ${result['error_message']}");
    }

  } catch (e) {
    print("Error printing image: $e");
  }
}
```

## ⚠️ **Important Notes:**

### Android Requirements

- ✅ **INTERNET permission** is now automatically included in the plugin
- ✅ **HTTPS/HTTP URLs** are now properly supported
- ✅ **Error logging** helps diagnose network issues

### iOS Requirements

- ✅ **HTTP URLs** require App Transport Security (ATS) configuration in `Info.plist` for HTTP (not HTTPS)
- ✅ **HTTPS URLs** work without additional configuration

### Network Considerations

- **Timeout**: Network requests have 10-15 second timeouts
- **Error Handling**: Failed downloads are logged and gracefully handled
- **Image Format**: Supports PNG, JPEG, and other common formats
- **Size Limits**: Large images may affect printing performance

### Example ATS Configuration (iOS, for HTTP only)

If you need to load HTTP (not HTTPS) images on iOS, add this to `ios/Runner/Info.plist`:

```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>
```

## 🔄 **Migration Guide:**

### Before (Broken on Android):

```dart
commands.appendBitmap(path: "https://example.com/image.png"); // ❌ FileNotFoundException
```

### After (Works on Both Platforms):

```dart
commands.appendBitmap(path: "https://example.com/image.png"); // ✅ Downloads and prints correctly
```

Your HTTPS image URLs like `https://image.tunai.io/image/ef5668d4-b2e8-41c2-a9eb-ac7d8941f93c.png` will now work correctly on both Android and iOS!
