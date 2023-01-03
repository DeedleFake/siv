import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

class Viewer extends StatelessWidget {
  final Uint8List imageBytes;

  const Viewer({super.key, required this.imageBytes});

  @override
  Widget build(BuildContext context) =>
      OrientationBuilder(builder: (context, orientation) {
        switch (orientation) {
          case Orientation.landscape:
            return Image.memory(imageBytes);
          case Orientation.portrait:
            return Image.memory(imageBytes);
        }
      });
}
