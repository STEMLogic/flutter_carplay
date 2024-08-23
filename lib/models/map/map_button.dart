import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

/// A button object for placement in a map.
class CPMapButton {
  /// Unique id of the object.
  final String _elementId;

  /// The enabled state of the map button.
  final bool isEnabled;

  /// The hidden state of the map button.
  final bool isHidden;

  /// Weather the map button is for Action strip of the map.
  ///
  /// Available only on Android Auto.
  final bool showInActionStrip;

  /// The image displayed on the map button.
  final String? image;

  /// The dark image displayed on the map button.
  final String? darkImage;

  /// The tintColor of the map button.
  final int? tintColor;

  /// The darkTintColor of the map button.
  final int? darkTintColor;

  /// The image displayed on the focused map button.
  final String? focusedImage;

  /// Fired when the user taps a map button.
  final VoidCallback onPressed;

  /// Creates [CPMapButton]
  CPMapButton({
    required this.onPressed,
    this.showInActionStrip = false,
    this.isEnabled = true,
    this.isHidden = false,
    this.focusedImage,
    this.darkTintColor,
    this.tintColor,
    this.darkImage,
    this.image,
    String? elementId,
  }) : _elementId = elementId ?? const Uuid().v4();

  Map<String, dynamic> toJson() => {
        'showInActionStrip': showInActionStrip,
        'darkTintColor': darkTintColor,
        'focusedImage': focusedImage,
        '_elementId': _elementId,
        'isEnabled': isEnabled,
        'darkImage': darkImage,
        'tintColor': tintColor,
        'isHidden': isHidden,
        'image': image,
      };

  String get uniqueId {
    return _elementId;
  }
}
