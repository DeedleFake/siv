import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
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
    final rsp = await http.get(Uri.parse(
        'https://chat.openai.com/_next/image?url=https%3A%2F%2Fs.gravatar.com%2Favatar%2F4eef6ac4f1e3d53b75375bc4509a3ba1%3Fs%3D480%26r%3Dpg%26d%3Dhttps%253A%252F%252Fcdn.auth0.com%252Favatars%252Fyi.png&w=32&q=75'));
    return image.decodeImage(rsp.bodyBytes) ?? image.Image.empty();
  }

  Future<void> _enableWakelock() async {
    try {
      await Wakelock.enable();
    } on PlatformException {
      if (kDebugMode) {
        print("Wakelock not supported on this platform.");
      }
    }
  }

  Future<void> _disableWakelock() async {
    if (await Wakelock.enabled) {
      Wakelock.disable();
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
            var imageData = snapshot.data?.toUint8List();
            return snapshot.hasData && (imageData != null)
                ? Viewer(imageBytes: imageData)
                : const CircularProgressIndicator();
          },
        ),
      );
}
