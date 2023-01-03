import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;

class Viewer extends StatelessWidget {
  final img.Image image;

  const Viewer({super.key, required this.image});

  @override
  Widget build(BuildContext context) =>
      OrientationBuilder(builder: (context, orientation) {
        switch (orientation) {
          case Orientation.landscape:
            return Image.memory(image.toUint8List());
          case Orientation.portrait:
            return Image.memory(img.copyRotate(image, angle: 90).toUint8List());
        }
      });
}
