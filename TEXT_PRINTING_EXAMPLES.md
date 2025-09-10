# 📝 Improved Text Printing Examples

This guide shows how to use the improved text printing functionality that fixes the small text size issues on Android.

## 🔧 **Issues Fixed:**

1. ✅ **Increased default bitmap text font size** from 25px to 48px
2. ✅ **Added native text printing functions** for better quality
3. ✅ **Added comprehensive text formatting options**

## 📋 **Usage Examples:**

### Basic Text Printing (Recommended)

```dart
import 'package:flutter_star_prnt/flutter_star_prnt.dart';

PrintCommands commands = PrintCommands();

// Set encoding for your language
commands.appendEncoding(StarEncoding.UTF8);

// Method 1: Simple text with font style
commands.appendText("Hello World!", fontStyle: StarFontStyleType.A);

// Method 2: Set font style first, then print
commands.appendFontStyle(StarFontStyleType.A); // Larger font (12x24 dots)
commands.appendText("This is large text\n");

commands.appendFontStyle(StarFontStyleType.B); // Smaller font (9x24 dots)
commands.appendText("This is smaller text\n");
```

### Advanced Text Formatting

```dart
PrintCommands commands = PrintCommands();

// Bold/emphasized text
commands.appendEmphasisText("BOLD TEXT\n");

// Inverted text (white on black)
commands.appendInvertText("INVERTED TEXT\n");

// Underlined text
commands.appendUnderlineText("UNDERLINED TEXT\n");

// Text with alignment
commands.appendTextWithAlignment("CENTER TEXT", StarAlignmentPosition.Center);
commands.appendLineFeed();
commands.appendTextWithAlignment("RIGHT TEXT", StarAlignmentPosition.Right);
commands.appendLineFeed();

// Text at specific position
commands.appendTextWithAbsolutePosition("Position 100", 100);
commands.appendLineFeed();
```

### Receipt Example

```dart
PrintCommands commands = PrintCommands();

// Header
commands.appendEncoding(StarEncoding.UTF8);
commands.appendFontStyle(StarFontStyleType.A);
commands.appendTextWithAlignment("STAR STORE", StarAlignmentPosition.Center);
commands.appendLineFeed(2);

// Receipt content
commands.appendFontStyle(StarFontStyleType.B);
commands.appendText("Receipt #12345\n");
commands.appendText("Date: ${DateTime.now()}\n");
commands.appendLineFeed();

// Items
commands.appendText("Item 1..................\$10.00\n");
commands.appendText("Item 2..................\$15.50\n");
commands.appendText("Tax.....................\$2.55\n");
commands.appendLineFeed();

// Total
commands.appendFontStyle(StarFontStyleType.A);
commands.appendEmphasisText("TOTAL: \$28.05\n");

// Cut paper
commands.appendCutPaper(StarCutPaperAction.PartialCutWithFeed);
```

### Bitmap Text (When You Need Custom Fonts)

```dart
PrintCommands commands = PrintCommands();

// Use bitmap text only when you need custom fonts or sizes
// Note: Now uses improved 48px default size instead of tiny 25px
commands.appendBitmapText(
  text: "Custom Font Text",
  fontSize: 60,  // Specify custom size
  width: 576,
  alignment: StarAlignmentPosition.Center,
);
```

## 🎯 **Font Size Comparison:**

| Method               | Quality    | Size Control | Performance | Use Case                     |
| -------------------- | ---------- | ------------ | ----------- | ---------------------------- |
| `appendText()`       | ⭐⭐⭐⭐⭐ | A/B fonts    | ⭐⭐⭐⭐⭐  | **Recommended** for all text |
| `appendBitmapText()` | ⭐⭐⭐     | Custom px    | ⭐⭐⭐      | Only for custom fonts/sizes  |

## 📱 **Complete Usage:**

```dart
Future<void> printReceipt(PortInfo port) async {
  PrintCommands commands = PrintCommands();

  // Set encoding
  commands.appendEncoding(StarEncoding.UTF8);

  // Use native text printing for best quality
  commands.appendFontStyle(StarFontStyleType.A);
  commands.appendText("Your receipt content here\n");

  // Send to printer
  await StarPrnt.sendCommands(
    portName: port.portName!,
    emulation: StarEmulation.StarPRNT,
    printCommands: commands,
  );
}
```

## 🔄 **Migration Guide:**

### Before (Small Text Issue):

```dart
commands.appendBitmapText(text: "Hello"); // Tiny 25px text
```

### After (Fixed):

```dart
// Option 1: Native text (recommended)
commands.appendText("Hello", fontStyle: StarFontStyleType.A);

// Option 2: Improved bitmap text
commands.appendBitmapText(text: "Hello", fontSize: 48); // Much larger default
```

The native text printing (`appendText`) will give you the best results with crisp, properly-sized text that uses the printer's built-in fonts.
