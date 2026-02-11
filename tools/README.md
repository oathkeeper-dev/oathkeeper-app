# Model Conversion Tools

This directory contains tools for converting the NSFW detection model to TensorFlow Lite format.

## Prerequisites

```bash
pip install tensorflow numpy pillow
```

## Model Conversion

To convert the GantMan/nsfw_model (MobileNet V2) to TensorFlow Lite:

```bash
cd tools
python convert_model.py
```

### What it does:

1. Downloads the MobileNet V2 NSFW model from GitHub releases
2. Converts it to TensorFlow Lite format with INT8 quantization
3. Saves it to `app/src/main/assets/nsfw_mobilenet_v2.tflite`
4. Verifies the converted model accuracy

### Expected Output:

- Model size: ~4MB after quantization
- Input shape: `[1, 224, 224, 3]` (RGB image)
- Output shape: `[1, 5]` (confidence scores for 5 classes)
- Classes: `drawings`, `hentai`, `neutral`, `porn`, `sexy`

## Manual Download

If automatic download fails, manually download from:
https://github.com/GantMan/nsfw_model/releases

Extract and place `nsfw_mobilenet2.h5` in this directory, then run:

```bash
python convert_model.py
```

## Model Information

- **Base Model**: MobileNet V2 (224x224)
- **Source**: https://github.com/GantMan/nsfw_model
- **License**: MIT
- **Accuracy**: ~90% on test dataset (original model)
- **Quantization**: INT8 for mobile optimization

## Troubleshooting

### Out of Memory Error

If you get OOM errors during conversion:

```bash
export TF_FORCE_GPU_ALLOW_GROWTH=true
python convert_model.py
```

### Large Model Size

The quantized model should be ~4MB. If it's larger:
- Ensure `converter.optimizations = [tf.lite.Optimize.DEFAULT]` is set
- Check that INT8 quantization is applied

### Accuracy Loss

Some accuracy loss (~1-2%) is expected with quantization. If loss > 5%:
- Try increasing representative dataset size
- Consider using full integer quantization only

## Verification

After conversion, verify the model works:

```python
import tensorflow as lite

interpreter = tf.lite.Interpreter(model_path="../app/src/main/assets/nsfw_mobilenet_v2.tflite")
interpreter.allocate_tensors()

print("Input details:", interpreter.get_input_details())
print("Output details:", interpreter.get_output_details())
```
