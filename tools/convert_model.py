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
    
    model_url = "https://github.com/GantMan/nsfw_model/releases/download/1.2.0/mobilenet_v2_140_224.1.zip"
    zip_path = "mobilenet_v2_140_224.1.zip"
    
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
    
    # Support both v1.1.0 and v1.2.0 directory structures
    possible_dirs = ["mobilenet_v2_140_224", "mobilenet_v2_140_224.1"]
    mobilenet_dir = None
    
    for dir_name in possible_dirs:
        if os.path.isdir(dir_name):
            mobilenet_dir = dir_name
            break
    
    model_path = None
    model = None
    
    if not mobilenet_dir:
        print(f"No model directory found")
        print(f"  Expected directories: {', '.join(possible_dirs)}")
        sys.exit(1)
    
    # Check for SavedModel directory structure (prioritize this over saved_model.h5 file
    # because that H5 file may contain custom KerasLayer objects that fail to load)
    files_in_dir = [f for f in os.listdir(mobilenet_dir) if not f.startswith('.')]
    # Check for SavedModel signature - must have saved_model.pb and/or variables directory
    has_saved_model_pb = any('saved_model.pb' in f.lower() for f in files_in_dir)
    is_saved_model = has_saved_model_pb or any(f == 'variables' and os.path.isdir(os.path.join(mobilenet_dir, f)) for f in files_in_dir)
    
    if is_saved_model:
        print(f"Detected SavedModel directory ({mobilenet_dir}) - will convert directly via from_saved_model")
        model_path = mobilenet_dir  # Set this to directory for later conversion
    elif os.path.exists(os.path.join(mobilenet_dir, "nsfw_mobilenet2.h5")):
        # Old format h5 models  
        model_path = os.path.join(mobilenet_dir, "nsfw_mobilenet2.h5")
        print(f"Loading H5 model from {mobilenet_dir}...")
        model = tf.keras.models.load_model(model_path)
    else:
        print(f"Unknown model structure in {mobilenet_dir}/")
        print("  Expected: nsfw_mobilenet2.h5 OR SavedModel directory (saved_model.pb + variables)")
        print(f"  Found files: {', '.join(files_in_dir)}")
        sys.exit(1)
    
    print("Converting to TensorFlow Lite...")
    
    # For SavedModel directories, use from_saved_model directly
    if model_path and os.path.isdir(model_path):
        try:
            converter = tf.lite.TFLiteConverter.from_saved_model(model_path)
        except Exception as e:
            # Handle potential error in saved model loading 
            print(f"Error with SavedModel conversion: {e}")
            # Try a fallback approach
            if os.path.exists(os.path.join(model_path, "saved_model.pb")):
                converter = tf.lite.TFLiteConverter.from_saved_model(os.path.join(model_path, "saved_model.pb"))
            else:
                raise e 
    else:
        # Standard H5 file case - go via Keras model
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Apply optimizations
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.int8]
    converter.representative_dataset = representative_dataset
    
    # Convert
    print("Running conversion (this may take a few minutes).")
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

def verify_accuracy(h5_file="nsfw_mobilenet2.h5"):
    """Verify model accuracy after conversion."""
    
    # Determine which directory to use for original model
    possible_dirs = ["mobilenet_v2_140_224", "mobilenet_v2_140_224.1"]
    original_model_path = None
    
    for dir_name in possible_dirs:
        potential_path = os.path.join(dir_name, h5_file)
        if os.path.exists(potential_path):
            original_model_path = potential_path
            break
    
    if not original_model_path or not os.path.exists(original_model_path):
        print("⚠ Skipping accuracy verification (original H5 model not found)")
        return
    
    print("\nVerifying model accuracy...")
    
    # Load both models
    original_model = tf.keras.models.load_model(original_model_path)
    
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
    
    # Check if model exists (support both v1.1.0 and v1.2.0)
    possible_dirs = ["mobilenet_v2_140_224", "mobilenet_v2_140_224.1"]
    model_found = False
    
    for dir_name in possible_dirs:
        if os.path.isdir(dir_name):
            # Check for H5 file or SavedModel structure
            h5_exists = os.path.exists(os.path.join(dir_name, "nsfw_mobilenet2.h5"))
            saved_model_h5 = os.path.exists(os.path.join(dir_name, "saved_model.h5"))
            
            if h5_exists or saved_model_h5:
                model_found = True
                break
            
            # Check for SavedModel directory structure
            files_in_dir = [f for f in os.listdir(dir_name) if not f.startswith('.')]
            is_saved_model = any('saved_model' in f.lower() or f == 'variables' for f in files_in_dir)
            
            if is_saved_model:
                model_found = True
                break
    
    if not model_found:
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
