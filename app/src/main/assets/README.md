# NSFW Model Placeholder

The TensorFlow Lite model file `nsfw_mobilenet_v2.tflite` should be placed in this directory.

## To Generate the Model:

1. Navigate to the tools directory:
   ```bash
   cd tools
   ```

2. Install dependencies:
   ```bash
   pip install tensorflow numpy pillow
   ```

3. Run the conversion script:
   ```bash
   python convert_model.py
   ```

This will:
- Download the GantMan/nsfw_model (MobileNet V2)
- Convert it to TensorFlow Lite format with INT8 quantization
- Place the model at `app/src/main/assets/nsfw_mobilenet_v2.tflite`

## Model Details:

- **Size**: ~4MB (after quantization)
- **Input**: 224x224 RGB image
- **Output**: 5 class probabilities
- **Classes**: drawings, hentai, neutral, porn, sexy
- **Format**: TensorFlow Lite (INT8 quantized)

## Manual Download:

If automatic download fails, manually download from:
https://github.com/GantMan/nsfw_model/releases

Place the `nsfw_mobilenet2.h5` file in the `tools/` directory and run the conversion script.
