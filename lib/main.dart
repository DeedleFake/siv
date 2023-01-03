import 'package:flutter/material.dart';
import 'package:siv/home.dart';

void main(List<String> args) {
  runApp(const Siv());
}

class Siv extends StatelessWidget {
  static const title = 'Siv';

  const Siv({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: title,
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      initialRoute: '/',
      onGenerateRoute: (settings) => MaterialPageRoute(
        builder: (context) {
          return Home(uri: Uri.file('/'));
        },
      ),
    );
  }
}
