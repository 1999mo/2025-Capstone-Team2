import 'dart:io';

import 'package:arcore_flutter_plugin/arcore_flutter_plugin.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  runApp(App());
}

class App extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData.dark(),
      home: ARViewScreen(),
    );
  }
}


class ARViewScreen extends StatefulWidget {
  @override
  _ARViewScreenState createState() => _ARViewScreenState();
}

class _ARViewScreenState extends State<ARViewScreen> {
  static const platform = MethodChannel('arcore_channel');
  int? textureId;

  @override
  void initState() {
    super.initState();
    _getTexture();
  }

  Future<void> _getTexture() async {
    try {
      final int id = await platform.invokeMethod('getARTexture');
      setState(() {
        textureId = id;
      });
    } on PlatformException catch (e) {
      print("AR 텍스쳐 호출 문제: '${e.message}");
    }
  }

  @override
  Widget build(BuildContext context) {
    if(textureId == null) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      body: Row(
        children: [
          Expanded(child: Texture(textureId: textureId!)),
        ],
      )
    );
  }
}
