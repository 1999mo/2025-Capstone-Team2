import 'package:arcore_flutter_plugin/arcore_flutter_plugin.dart';
import 'package:flutter/material.dart';

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
  ArCoreController? arCoreController;

  void onArCoreViewCreated(ArCoreController controller) {
    arCoreController = controller;
  }

  @override
  void dispose() {
    arCoreController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ArCoreView(
        onArCoreViewCreated: onArCoreViewCreated,
      ),
    );
  }
}