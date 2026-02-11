#!/usr/bin/env python3
"""
NSFW Model Conversion Script

Converts the GantMan/nsfw_model (MobileNet V2) to TensorFlow Lite format
with INT8 quantization for mobile deployment.

Prerequisites:
    pip install tensorflow numpy pillow

Usage:
    python convert_model.py

Output:
    app/src/main/assets/nsfw_mobilenet_v2.tflite
"""

import tensorflow as tf
import numpy as np
import os
import sys

def download_model():
    """Download the NSFW model from GitHub releases."""
    import urllib.request
    
    model_url = "https://github.com/GantMan/nsfw_model/releases/download/1.1.0/mobilenet_v2_140_224.zip"
    zip_path = "mobilenet_v2_140_224.zip"
    
    print("Downloading NSFW model...")
    urllib.request.urlretrieve(model_url, zip_path)
    
    import zipfile
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(".")
    
    os.remove(zip_path)
    print("Model downloaded and extracted")

def representative_dataset():
    """Generate representative dataset for quantization."""
    for _ in range(100):
        # Generate random data in the range [0, 1]
        data = np.random.rand(1, 224, 224, 3).astype(np.float32)
        yield [data]

def convert_model():
    """Convert the model to TensorFlow Lite format."""
    
    model_path = "mobilenet_v2_140_224/nsfw_mobilenet2.h5"
    
    if not os.path.exists(model_path):
        print(f"Model file not found: {model_path}")
        print("Please download the model from:")
        print("https://github.com/GantMan/nsfw_model/releases")
        sys.exit(1)
    
    print("Loading model...")
    model = tf.keras.models.load_model(model_path)
    
    print("Converting to TensorFlow Lite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Apply optimizations
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.int8]
    converter.representative_dataset = representative_dataset
    
    # Convert
    print("Running conversion (this may take a few minutes)...")
    tflite_model = converter.convert()
    
    # Save the model
    output_dir = "../app/src/main/assets"
    os.makedirs(output_dir, exist_ok=True)
    
    output_path = os.path.join(output_dir, "nsfw_mobilenet_v2.tflite")
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    # Get model size
    model_size_mb = len(tflite_model) / (1024 * 1024)
    print(f"✓ Model converted successfully!")
    print(f"  Location: {output_path}")
    print(f"  Size: {model_size_mb:.2f} MB")
    
    # Test the model
    print("\nTesting model...")
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"  Input shape: {input_details[0]['shape']}")
    print(f"  Output shape: {output_details[0]['shape']}")
    print(f"  Expected classes: 5 (drawings, hentai, neutral, porn, sexy)")
    
    # Run a test inference
    test_input = np.random.rand(1, 224, 224, 3).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    output_data = interpreter.get_tensor(output_details[0]['index'])
    
    print(f"  Test inference output shape: {output_data.shape}")
    print("✓ Model test passed!")

def verify_accuracy():
    """Verify model accuracy after conversion."""
    print("\nVerifying model accuracy...")
    
    # Load both models
    original_model = tf.keras.models.load_model("mobilenet_v2_140_224/nsfw_mobilenet2.h5")
    
    interpreter = tf.lite.Interpreter(model_path="../app/src/main/assets/nsfw_mobilenet_v2.tflite")
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # Test with sample data
    test_images = np.random.rand(10, 224, 224, 3).astype(np.float32)
    
    # Get predictions from both models
    original_preds = original_model.predict(test_images)
    
    tflite_preds = []
    for img in test_images:
        interpreter.set_tensor(input_details[0]['index'], [img])
        interpreter.invoke()
        tflite_preds.append(interpreter.get_tensor(output_details[0]['index'])[0])
    tflite_preds = np.array(tflite_preds)
    
    # Calculate accuracy difference
    diff = np.abs(original_preds - tflite_preds)
    max_diff = np.max(diff)
    mean_diff = np.mean(diff)
    
    print(f"  Max difference: {max_diff:.6f}")
    print(f"  Mean difference: {mean_diff:.6f}")
    
    if max_diff < 0.1:
        print("✓ Accuracy within acceptable range")
    else:
        print("⚠ Warning: Large accuracy difference detected")

if __name__ == "__main__":
    print("=" * 60)
    print("NSFW Model Conversion Tool")
    print("=" * 60)
    print()
    
    # Check if model exists
    if not os.path.exists("mobilenet_v2_140_224/nsfw_mobilenet2.h5"):
        print("Model not found locally.")
        response = input("Download model from GitHub? (y/n): ")
        if response.lower() == 'y':
            download_model()
        else:
            print("Please download the model manually from:")
            print("https://github.com/GantMan/nsfw_model/releases")
            sys.exit(0)
    
    try:
        convert_model()
        verify_accuracy()
        print()
        print("=" * 60)
        print("Conversion complete!")
        print("=" * 60)
    except Exception as e:
        print(f"\n✗ Error: {e}")
        sys.exit(1)
