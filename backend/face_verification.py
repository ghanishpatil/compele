import cv2
import numpy as np
import face_recognition
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Constants for verification
MINIMUM_CONFIDENCE_THRESHOLD = 0.45  # 45%
HIGH_CONFIDENCE_THRESHOLD = 0.55  # 55%

def verify_faces(reference_image_path, captured_image_path):
    """
    Verify if two face images match
    
    Args:
        reference_image_path: Path to the reference image file
        captured_image_path: Path to the captured image file
        
    Returns:
        dict: Verification result with keys:
            - verified (bool): Whether the faces match
            - confidence (float): Confidence level (0-1)
            - message (str): Descriptive message of the result
    """
    try:
        # Load reference image
        logger.info(f"Loading reference image from {reference_image_path}")
        reference_image = face_recognition.load_image_file(reference_image_path)
        
        # Load captured image
        logger.info(f"Loading captured image from {captured_image_path}")
        captured_image = face_recognition.load_image_file(captured_image_path)
        
        # Find faces in reference image
        logger.info("Detecting faces in reference image")
        reference_face_locations = face_recognition.face_locations(reference_image)
        
        if not reference_face_locations:
            logger.warning("No face detected in reference image")
            return {
                'verified': False,
                'confidence': 0.0,
                'message': 'No face detected in reference image'
            }
        
        # Find faces in captured image
        logger.info("Detecting faces in captured image")
        captured_face_locations = face_recognition.face_locations(captured_image)
        
        if not captured_face_locations:
            logger.warning("No face detected in captured image")
            return {
                'verified': False,
                'confidence': 0.0,
                'message': 'No face detected in captured image'
            }
        
        # Encode faces
        logger.info("Encoding reference face")
        reference_face_encoding = face_recognition.face_encodings(reference_image, reference_face_locations)[0]
        
        logger.info("Encoding captured face")
        captured_face_encoding = face_recognition.face_encodings(captured_image, captured_face_locations)[0]
        
        # Compare faces
        logger.info("Comparing face encodings")
        face_distance = face_recognition.face_distance([reference_face_encoding], captured_face_encoding)[0]
        
        # Calculate confidence (1 - distance)
        confidence = 1.0 - float(face_distance)
        logger.info(f"Face distance: {face_distance}, Confidence: {confidence:.4f}")
        
        # Determine if there's a match based on confidence thresholds
        is_match = confidence >= MINIMUM_CONFIDENCE_THRESHOLD
        
        # Generate result message
        if is_match:
            if confidence >= HIGH_CONFIDENCE_THRESHOLD:
                message = "High confidence match"
            else:
                message = "Low confidence match"
        else:
            message = "Face verification failed"
        
        return {
            'verified': is_match,
            'confidence': float(confidence),
            'message': message
        }
        
    except Exception as e:
        logger.error(f"Error in face verification: {str(e)}")
        return {
            'verified': False,
            'confidence': 0.0,
            'message': f'Error in face verification: {str(e)}'
        }


def draw_face_boxes(image_path, output_path=None):
    """
    Draw boxes around detected faces in an image
    Useful for debugging and visualization
    
    Args:
        image_path: Path to the input image
        output_path: Path to save the output image (optional)
        
    Returns:
        numpy.ndarray: The image with face boxes drawn
    """
    try:
        # Load image
        image = cv2.imread(image_path)
        
        # Convert to RGB for face_recognition
        rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        
        # Detect faces
        face_locations = face_recognition.face_locations(rgb_image)
        
        # Draw boxes around faces
        for (top, right, bottom, left) in face_locations:
            cv2.rectangle(image, (left, top), (right, bottom), (0, 255, 0), 2)
        
        # Save output image if specified
        if output_path:
            cv2.imwrite(output_path, image)
        
        return image
        
    except Exception as e:
        logger.error(f"Error drawing face boxes: {str(e)}")
        return None 