import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as image;
import 'package:siv/viewer.dart';
import 'package:wakelock/wakelock.dart';

class Home extends StatefulWidget {
  final Uri uri;

  const Home({super.key, required this.uri});

  @override
  State<Home> createState() => _HomeState();
}

class _HomeState extends State<Home> {
  late Future<image.Image> _image;

  Future<image.Image> _loadImage() async {
    final file = File.fromUri(widget.uri);
    return image.decodeImage(await file.readAsBytes()) ?? image.Image.empty();
  }

  Future<void> _enableWakelock() async {
    try {
      await Wakelock.enable();
      if (kDebugMode) {
        print('Wakelock enabled.');
      }
    } on PlatformException {
      if (kDebugMode) {
        print("Wakelock not supported on this platform.");
      }
    }
  }

  Future<void> _disableWakelock() async {
    if (await Wakelock.enabled) {
      await Wakelock.disable();
      if (kDebugMode) {
        print('Wakelock disabled.');
      }
    }
  }

  @override
  void initState() {
    super.initState();

    _image = _loadImage();
    _enableWakelock();
  }

  @override
  void dispose() {
    super.dispose();

    _disableWakelock();
  }

  @override
  Widget build(BuildContext context) => Center(
        child: FutureBuilder(
          future: _image,
          builder: (context, snapshot) {
            var image = snapshot.data;
            return snapshot.hasData && (image != null)
                ? Viewer(image: image)
                : const CircularProgressIndicator();
          },
        ),
      );
}
