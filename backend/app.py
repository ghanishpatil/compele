from flask import Flask, request, jsonify
from flask_cors import CORS
import bcrypt
import jwt
import datetime
from functools import wraps
import os
import re
import json
import firebase_admin
from firebase_admin import credentials, auth, firestore, storage
import uuid
import tempfile
import base64
import logging
import face_recognition

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Define confidence thresholds for face verification
MINIMUM_CONFIDENCE_THRESHOLD = 0.45  # 45% minimum confidence required for a match
HIGH_CONFIDENCE_THRESHOLD = 0.55     # 55% confidence for high confidence match

app = Flask(__name__)
CORS(app)

def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None
        if 'Authorization' in request.headers:
            token = request.headers['Authorization'].split(' ')[1]
        
        if not token:
            return jsonify({'message': 'Token is missing'}), 401
        
        try:
            # Verify the token with Firebase Admin SDK
            decoded_token = auth.verify_id_token(token)
            current_user = decoded_token
        except Exception as e:
            return jsonify({'message': 'Token is invalid'}), 401
            
        return f(*args, **kwargs)
    return decorated

# Initialize Firebase Admin with your service account
cred = credentials.Certificate('startup-cf3fd-firebase-adminsdk-fbsvc-893409a921.json')
firebase_admin.initialize_app(cred, {
    'storageBucket': 'startup-cf3fd.firebasestorage.app'  # Updated correct bucket name
})

# Get Firestore client
db = firestore.client()

# Get Storage bucket
bucket = storage.bucket()

# In production, use environment variables for these
app.config['SECRET_KEY'] = 'your-secret-key'  # Change this in production

def validate_sevarth_id(sevarth_id):
    # Must contain both letters and numbers, minimum 6 characters
    return bool(re.match(r'^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,}$', sevarth_id))

def validate_name(name):
    # Only alphabets and spaces allowed
    return bool(re.match(r'^[A-Za-z\s]+$', name))

def validate_email(email):
    return bool(re.match(r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$', email))

def validate_phone(phone):
    # 10 digits phone number
    return bool(re.match(r'^\d{10}$', phone))

@app.route('/register/admin', methods=['POST'])
def register_admin():
    data = request.get_json()
    
    # Extract fields
    organization_name = data.get('organization_name')
    first_name = data.get('first_name')
    last_name = data.get('last_name')
    sevarth_id = data.get('sevarth_id')
    email = data.get('email')
    contact_number = data.get('contact_number')
    password = data.get('password')

    # Validate required fields
    if not all([organization_name, first_name, last_name, sevarth_id, email, contact_number, password]):
        return jsonify({
            'message': 'All fields are required',
            'success': False
        }), 400

    # Validate organization name (only alphabets)
    if not validate_name(organization_name):
        return jsonify({
            'message': 'Organization name must contain only alphabets',
            'success': False
        }), 400

    # Validate first name (only alphabets)
    if not validate_name(first_name):
        return jsonify({
            'message': 'First name must contain only alphabets',
            'success': False
        }), 400

    # Validate last name (only alphabets)
    if not validate_name(last_name):
        return jsonify({
            'message': 'Last name must contain only alphabets',
            'success': False
        }), 400

    # Validate Sevarth ID format (must contain both alphabets and numbers)
    if not validate_sevarth_id(sevarth_id):
        return jsonify({
            'message': 'Sevarth ID must contain both alphabets and numbers (minimum 6 characters)',
            'success': False
        }), 400

    # Validate email format
    if not validate_email(email):
        return jsonify({
            'message': 'Invalid email format',
            'success': False
        }), 400

    # Validate phone number
    if not validate_phone(contact_number):
        return jsonify({
            'message': 'Invalid contact number format',
            'success': False
        }), 400

    try:
        # Check if user already exists in Firebase
        admin_ref = db.collection('admins').document(sevarth_id)
        if admin_ref.get().exists:
            return jsonify({
                'message': 'Sevarth ID already registered',
                'success': False
            }), 409

        # Create user in Firebase Authentication
        user = auth.create_user(
            email=email,
            password=password,
            display_name=f"{first_name} {last_name}"
        )

        # Store additional admin details in Firestore
        admin_data = {
            'organization_name': organization_name,
            'first_name': first_name,
            'last_name': last_name,
            'sevarth_id': sevarth_id,
            'email': email,
            'contact_number': contact_number,
            'uid': user.uid,
            'role': 'admin',
            'created_at': firestore.SERVER_TIMESTAMP
        }
        
        admin_ref.set(admin_data)

        return jsonify({
            'message': 'Admin registered successfully',
            'success': True
        })

    except Exception as e:
        print(f"Error registering admin: {str(e)}")
        return jsonify({
            'message': 'Registration failed',
            'success': False
        }), 500

@app.route('/login', methods=['POST'])
def login():
    data = request.get_json()
    sevarth_id = data.get('sevarth_id')
    password = data.get('password')
    role = data.get('role')
    # Get optional name data if provided
    first_name = data.get('first_name')
    last_name = data.get('last_name')

    print(f"Login attempt: sevarth_id={sevarth_id}, role={role}")

    if not all([sevarth_id, password, role]):
        return jsonify({'message': 'Missing required fields'}), 400

    try:
        # For admin login, first check if admin exists in Firestore
        if role == 'admin':
            admin_ref = db.collection('admins').document(sevarth_id)
            admin_doc = admin_ref.get()
            
            if not admin_doc.exists:
                print(f"Admin not found with sevarth_id={sevarth_id}")
                return jsonify({'message': 'Admin account not found. Please register first.'}), 401
            
            admin_data = admin_doc.to_dict()
            email = admin_data.get('email')
            
            try:
                # Try to sign in with Firebase Auth
                firebase_user = auth.get_user_by_email(email)
                # Create a custom token
                custom_token = auth.create_custom_token(firebase_user.uid)
                
                # Prepare admin response
                admin_response = {
                    'sevarth_id': sevarth_id,
                    'email': email,
                    'name': f"{admin_data.get('first_name', '')} {admin_data.get('last_name', '')}".strip(),
                    'role': 'admin',
                    'organization_name': admin_data.get('organization_name')
                }

                print(f"Admin login successful for {sevarth_id}")
                return jsonify({
                    'token': custom_token.decode(),
                    'user': admin_response,
                    'message': 'Login successful'
                })
                
            except auth.UserNotFoundError:
                print(f"Firebase user not found for admin {sevarth_id}")
                return jsonify({'message': 'Invalid admin credentials'}), 401
            except Exception as e:
                print(f"Error during admin login: {str(e)}")
                return jsonify({'message': 'Authentication failed'}), 401
        
        # For regular user login
        else:
            try:
                # For testing purposes, construct email from sevarth_id
                email = f"{sevarth_id}@example.com"
                firebase_user = auth.get_user_by_email(email)
                
                # Get existing user data
                user_ref = db.collection('users').where('uid', '==', firebase_user.uid).limit(1)
                users = list(user_ref.stream())
                
                if not users:
                    print(f"User not found with uid={firebase_user.uid}")
                    return jsonify({'message': 'User not found'}), 404
                
                user_doc = users[0]
                user_data = user_doc.to_dict()
                
                # Create a custom token
                custom_token = auth.create_custom_token(firebase_user.uid)
                
                # Prepare user response
                user_response = {
                    'sevarth_id': sevarth_id,
                    'email': user_data.get('email'),
                    'name': f"{user_data.get('firstName', '')} {user_data.get('lastName', '')}".strip(),
                    'role': 'user'
                }

                print(f"User login successful for {sevarth_id}")
                return jsonify({
                    'token': custom_token.decode(),
                    'user': user_response,
                    'message': 'Login successful'
                })
                
            except auth.UserNotFoundError:
                # Create the user if they don't exist
                try:
                    # If names weren't provided, extract from sevarth_id or use defaults
                    if not first_name or not last_name:
                        # Try to extract name from sevarth_id (assuming format: firstname_lastname)
                        name_parts = sevarth_id.split('_')
                        if len(name_parts) >= 2:
                            first_name = name_parts[0].capitalize()
                            last_name = name_parts[1].capitalize()
                        else:
                            # Use sevarth_id as first name if no clear name format
                            first_name = sevarth_id
                            last_name = ""
                    
                    # Create user in Firebase Auth
                    firebase_user = auth.create_user(
                        email=email,
                        password=password,
                        display_name=f"{first_name} {last_name}".strip()
                    )
                    
                    # Create user document in Firestore
                    user_data = {
                        'uid': firebase_user.uid,
                        'sevarthId': sevarth_id,
                        'email': email,
                        'firstName': first_name,
                        'lastName': last_name,
                        'role': 'user',
                        'createdAt': firestore.SERVER_TIMESTAMP
                    }
                    
                    # Store in Firestore
                    db.collection('users').document(firebase_user.uid).set(user_data)
                    print(f"Created new user: {user_data}")
                    
                    # Create a custom token
                    custom_token = auth.create_custom_token(firebase_user.uid)
                    
                    # Prepare user response
                    user_response = {
                        'sevarth_id': sevarth_id,
                        'email': email,
                        'name': f"{first_name} {last_name}".strip(),
                        'role': 'user'
                    }
                    
                    return jsonify({
                        'token': custom_token.decode(),
                        'user': user_response,
                        'message': 'User created and logged in successfully'
                    })
                    
                except Exception as e:
                    print(f"Error creating user: {str(e)}")
                    return jsonify({'message': f'Error creating user: {str(e)}'}), 500

    except Exception as e:
        print(f"Error during login: {str(e)}")
        return jsonify({'message': f'Login failed: {str(e)}'}), 500

@app.route('/protected', methods=['GET'])
@token_required
def protected():
    return jsonify({'message': 'This is a protected route'})

@app.route('/admin/users', methods=['GET'])
@token_required
def get_users():
    try:
        # Get all users with role "user"
        users_ref = db.collection('users').where('role', '==', 'user')
        users = []
        
        for doc in users_ref.stream():
            user_data = doc.to_dict()
            # Remove sensitive information
            if 'password' in user_data:
                del user_data['password']
            users.append(user_data)
            
        return jsonify({
            'users': users,
            'success': True
        })
    except Exception as e:
        print(f"Error fetching users: {str(e)}")
        return jsonify({
            'message': 'Failed to fetch users',
            'success': False
        }), 500

@app.route('/admin/locations', methods=['GET'])
@token_required
def get_locations():
    try:
        # Get all office locations
        locations_ref = db.collection('office_locations')
        locations = []
        
        for doc in locations_ref.stream():
            location_data = doc.to_dict()
            locations.append(location_data)
            
        return jsonify({
            'locations': locations,
            'success': True
        })
    except Exception as e:
        print(f"Error fetching locations: {str(e)}")
        return jsonify({
            'message': 'Failed to fetch locations',
            'success': False
        }), 500

@app.route('/admin/locations/<location_id>', methods=['GET'])
@token_required
def get_location(location_id):
    try:
        # Get specific office location
        location_ref = db.collection('office_locations').document(location_id)
        location_doc = location_ref.get()
        
        if not location_doc.exists:
            return jsonify({
                'message': 'Location not found',
                'success': False
            }), 404
            
        location_data = location_doc.to_dict()
        
        return jsonify({
            'location': location_data,
            'success': True
        })
    except Exception as e:
        print(f"Error fetching location: {str(e)}")
        return jsonify({
            'message': 'Failed to fetch location',
            'success': False
        }), 500

@app.route('/admin/locations', methods=['POST'])
@token_required
def add_location(current_user):
    data = request.get_json()
    
    # Extract fields
    name = data.get('name')
    taluka = data.get('taluka')
    latitude = data.get('latitude')
    longitude = data.get('longitude')
    radius = data.get('radius', 100)  # Default radius is 100m
    
    # Validate required fields
    if not all([name, latitude, longitude]):
        return jsonify({
            'message': 'Name, latitude, and longitude are required',
            'success': False
        }), 400
    
    # Validate coordinates
    try:
        latitude = float(latitude)
        longitude = float(longitude)
        radius = int(radius)
        
        if latitude < -90 or latitude > 90:
            return jsonify({
                'message': 'Latitude must be between -90 and 90',
                'success': False
            }), 400
            
        if longitude < -180 or longitude > 180:
            return jsonify({
                'message': 'Longitude must be between -180 and 180',
                'success': False
            }), 400
            
        if radius <= 0:
            return jsonify({
                'message': 'Radius must be greater than 0',
                'success': False
            }), 400
    except ValueError:
        return jsonify({
            'message': 'Invalid coordinate format',
            'success': False
        }), 400
    
    try:
        # Generate a unique ID
        location_id = str(uuid.uuid4())
        
        # Create the location document
        location_data = {
            'id': location_id,
            'name': name,
            'taluka': taluka,
            'latitude': latitude,
            'longitude': longitude,
            'radius': radius,
            'created_by': current_user['uid'],
            'created_at': firestore.SERVER_TIMESTAMP,
            'updated_at': firestore.SERVER_TIMESTAMP
        }
        
        # Save to Firestore
        db.collection('office_locations').document(location_id).set(location_data)
        
        return jsonify({
            'message': 'Location added successfully',
            'location_id': location_id,
            'success': True
        })
    except Exception as e:
        print(f"Error adding location: {str(e)}")
        return jsonify({
            'message': 'Failed to add location',
            'success': False
        }), 500

@app.route('/admin/locations/<location_id>', methods=['PUT'])
@token_required
def update_location(current_user, location_id):
    data = request.get_json()
    
    # Extract fields
    name = data.get('name')
    taluka = data.get('taluka')
    latitude = data.get('latitude')
    longitude = data.get('longitude')
    radius = data.get('radius', 100)
    
    # Validate required fields
    if not all([name, latitude, longitude]):
        return jsonify({
            'message': 'Name, latitude, and longitude are required',
            'success': False
        }), 400
    
    # Validate coordinates
    try:
        latitude = float(latitude)
        longitude = float(longitude)
        radius = int(radius)
        
        if latitude < -90 or latitude > 90:
            return jsonify({
                'message': 'Latitude must be between -90 and 90',
                'success': False
            }), 400
            
        if longitude < -180 or longitude > 180:
            return jsonify({
                'message': 'Longitude must be between -180 and 180',
                'success': False
            }), 400
            
        if radius <= 0:
            return jsonify({
                'message': 'Radius must be greater than 0',
                'success': False
            }), 400
    except ValueError:
        return jsonify({
            'message': 'Invalid coordinate format',
            'success': False
        }), 400
    
    try:
        # Check if location exists
        location_ref = db.collection('office_locations').document(location_id)
        if not location_ref.get().exists:
            return jsonify({
                'message': 'Location not found',
                'success': False
            }), 404
        
        # Update the location document
        location_data = {
            'name': name,
            'taluka': taluka,
            'latitude': latitude,
            'longitude': longitude,
            'radius': radius,
            'updated_at': firestore.SERVER_TIMESTAMP
        }
        
        # Update in Firestore
        location_ref.update(location_data)
        
        return jsonify({
            'message': 'Location updated successfully',
            'success': True
        })
    except Exception as e:
        print(f"Error updating location: {str(e)}")
        return jsonify({
            'message': 'Failed to update location',
            'success': False
        }), 500

@app.route('/admin/locations/<location_id>', methods=['DELETE'])
@token_required
def delete_location(current_user, location_id):
    try:
        # Check if location exists
        location_ref = db.collection('office_locations').document(location_id)
        if not location_ref.get().exists:
            return jsonify({
                'message': 'Location not found',
                'success': False
            }), 404
        
        # Delete the location
        location_ref.delete()
        
        return jsonify({
            'message': 'Location deleted successfully',
            'success': True
        })
    except Exception as e:
        print(f"Error deleting location: {str(e)}")
        return jsonify({
            'message': 'Failed to delete location',
            'success': False
        }), 500

@app.route('/register/user', methods=['POST'])
def register_user():
    data = request.get_json()
    
    # Extract fields
    sevarth_id = data.get('sevarth_id')
    first_name = data.get('first_name')
    last_name = data.get('last_name')
    gender = data.get('gender')
    date_of_birth = data.get('date_of_birth')
    phone_number = data.get('phone_number')
    email = data.get('email')
    location_id = data.get('location_id')
    location_name = data.get('location_name')
    password = data.get('password')

    # Validate required fields
    if not all([sevarth_id, first_name, last_name, gender, date_of_birth, phone_number, email, location_id, password]):
        return jsonify({
            'message': 'All fields are required',
            'success': False
        }), 400

    # Validate first name (only alphabets)
    if not validate_name(first_name):
        return jsonify({
            'message': 'First name must contain only alphabets',
            'success': False
        }), 400

    # Validate last name (only alphabets)
    if not validate_name(last_name):
        return jsonify({
            'message': 'Last name must contain only alphabets',
            'success': False
        }), 400

    # Validate Sevarth ID format (must contain both alphabets and numbers)
    if not validate_sevarth_id(sevarth_id):
        return jsonify({
            'message': 'Sevarth ID must contain both alphabets and numbers (minimum 6 characters)',
            'success': False
        }), 400

    # Validate email format
    if not validate_email(email):
        return jsonify({
            'message': 'Invalid email format',
            'success': False
        }), 400

    # Validate phone number
    if not validate_phone(phone_number):
        return jsonify({
            'message': 'Invalid contact number format',
            'success': False
        }), 400

    try:
        # Check if user already exists in Firebase
        user_ref = db.collection('users').document(sevarth_id)
        if user_ref.get().exists:
            return jsonify({
                'message': 'Sevarth ID already registered',
                'success': False
            }), 409

        # Create user in Firebase Authentication
        user = auth.create_user(
            email=email,
            password=password,
            display_name=f"{first_name} {last_name}"
        )

        # Generate a unique ID
        user_id = str(uuid.uuid4())
        
        # Get admin info (the person creating the user)
        token = request.headers.get('Authorization').split(' ')[1]
        decoded_token = auth.verify_id_token(token)
        admin_uid = decoded_token['uid']

        # Store user details in Firestore
        user_data = {
            'id': user_id,
            'sevarthId': sevarth_id,
            'firstName': first_name,
            'lastName': last_name,
            'gender': gender,
            'dateOfBirth': date_of_birth,
            'phoneNumber': phone_number,
            'email': email,
            'locationId': location_id,
            'locationName': location_name,
            'uid': user.uid,
            'role': 'user',
            'active': True,
            'createdBy': admin_uid,
            'createdAt': firestore.SERVER_TIMESTAMP,
            'updatedAt': firestore.SERVER_TIMESTAMP
        }
        
        user_ref.set(user_data)

        return jsonify({
            'message': 'User registered successfully',
            'success': True,
            'user_id': user_id
        })

    except Exception as e:
        print(f"Error registering user: {str(e)}")
        return jsonify({
            'message': 'Registration failed: ' + str(e),
            'success': False
        }), 500

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({'status': 'ok', 'message': 'Face Recognition Backend is running'}), 200

@app.route('/api/register-face', methods=['POST'])
def register_face():
    """
    Endpoint to register a user's face
    Request body should contain:
    - sevarth_id: User's Sevarth ID
    - face_image: Base64 encoded image
    """
    data = request.get_json()
    
    if not data:
        return jsonify({'message': 'No data provided'}), 400
    
    sevarth_id = data.get('sevarth_id')
    face_image_b64 = data.get('face_image')
    
    if not sevarth_id or not face_image_b64:
        return jsonify({'message': 'Missing required fields'}), 400
    
    try:
        # Convert base64 to image file
        image_data = base64.b64decode(face_image_b64.split(',')[1] if ',' in face_image_b64 else face_image_b64)
        
        # Create a temporary file to store the image
        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.jpg')
        temp_file.write(image_data)
        temp_file.close()
        
        # Upload to Firebase Storage with the correct naming pattern
        image_path = f"reference_images/face_{sevarth_id}.jpg"
        logger.info(f"Uploading reference image to path: {image_path}")
        blob = storage.bucket().blob(image_path)
        blob.upload_from_filename(temp_file.name)
        
        # Clean up temporary file
        os.unlink(temp_file.name)
        
        # Update user record in Firestore
        user_ref = db.collection('users').document(sevarth_id)
        user_ref.update({
            'has_face_image': True,
            'face_image_url': blob.public_url,
            'face_updated_at': firestore.SERVER_TIMESTAMP
        })
        
        return jsonify({
            'message': 'Face registered successfully',
            'face_image_url': blob.public_url
        }), 200
        
    except Exception as e:
        logger.error(f"Error registering face: {str(e)}")
        return jsonify({'message': f'Error registering face: {str(e)}'}), 500

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
    finally:
        # Ensure we clean up any resources
        try:
            del reference_image
            del captured_image
        except:
            pass

@app.route('/api/verify-face', methods=['POST'])
def verify_face():
    temp_capture_file = None
    temp_ref_file = None
    
    try:
        data = request.get_json()
        if not data:
            return jsonify({'message': 'No data provided'}), 400
        
        sevarth_id = data.get('sevarth_id')
        face_image_b64 = data.get('face_image')
        
        if not sevarth_id or not face_image_b64:
            return jsonify({'message': 'Missing required fields'}), 400
        
        # Check if user has a reference image
        ref_image_path = f"reference_images/face_{sevarth_id}.jpg"
        logger.info(f"Looking for reference image at path: {ref_image_path}")
        blob = storage.bucket().blob(ref_image_path)
        
        if not blob.exists():
            logger.error(f"No reference image found at {ref_image_path}")
            return jsonify({'message': 'No reference image found for this user'}), 404
        
        # Create unique temporary file names
        import uuid
        temp_capture_path = os.path.join(tempfile.gettempdir(), f'capture_{uuid.uuid4()}.jpg')
        temp_ref_path = os.path.join(tempfile.gettempdir(), f'ref_{uuid.uuid4()}.jpg')
        
        # Convert and save captured image
        image_data = base64.b64decode(face_image_b64.split(',')[1] if ',' in face_image_b64 else face_image_b64)
        with open(temp_capture_path, 'wb') as f:
            f.write(image_data)
        
        # Download reference image
        blob.download_to_filename(temp_ref_path)
        
        # Verify faces
        verification_result = verify_faces(temp_ref_path, temp_capture_path)
        
        return jsonify(verification_result), 200
        
    except Exception as e:
        logger.error(f"Error verifying face: {str(e)}")
        return jsonify({'message': f'Error verifying face: {str(e)}'}), 500
        
    finally:
        # Clean up temporary files
        try:
            if os.path.exists(temp_capture_path):
                os.remove(temp_capture_path)
            if os.path.exists(temp_ref_path):
                os.remove(temp_ref_path)
        except Exception as e:
            logger.error(f"Error cleaning up temporary files: {str(e)}")

@app.route('/api/mark-attendance', methods=['POST'])
def mark_attendance():
    """
    Endpoint to mark attendance
    Request body should contain:
    - sevarth_id: User's Sevarth ID
    - type: "check_in" or "check_out"
    - verification_confidence: Confidence score from face verification
    """
    data = request.get_json()
    
    if not data:
        return jsonify({'message': 'No data provided'}), 400
    
    sevarth_id = data.get('sevarth_id')
    attendance_type = data.get('type')
    verification_confidence = data.get('verification_confidence', 0)
    
    if not sevarth_id or not attendance_type:
        return jsonify({'message': 'Missing required fields'}), 400
    
    if attendance_type not in ['check_in', 'check_out']:
        return jsonify({'message': 'Invalid attendance type. Must be "check_in" or "check_out"'}), 400
    
    try:
        # Get current date and time
        now = datetime.datetime.now()
        date_str = now.strftime("%Y-%m-%d")
        time_str = now.strftime("%H:%M:%S")
        
        # Get user details from Firestore
        user_ref = db.collection('users').where('sevarthId', '==', sevarth_id).limit(1)
        user_docs = user_ref.get()
        
        if not user_docs:
            return jsonify({'message': 'User not found'}), 404
        
        user_data = user_docs[0].to_dict()
        user_name = f"{user_data.get('firstName', '')} {user_data.get('lastName', '')}"
        
        # Create attendance record
        attendance_data = {
            'userId': sevarth_id,
            'userName': user_name,
            'date': date_str,
            'time': time_str,
            'type': attendance_type,
            'status': 'Present',
            'verificationConfidence': verification_confidence,
            'timestamp': firestore.SERVER_TIMESTAMP
        }
        
        # Add to Firestore
        attendance_ref = db.collection('face-recognition-attendance').document()
        attendance_ref.set(attendance_data)
        
        return jsonify({
            'message': f'Attendance {attendance_type} marked successfully',
            'attendance_id': attendance_ref.id,
            'date': date_str,
            'time': time_str
        }), 200
        
    except Exception as e:
        logger.error(f"Error marking attendance: {str(e)}")
        return jsonify({'message': f'Error marking attendance: {str(e)}'}), 500

@app.route('/api/attendance-history', methods=['GET'])
def get_attendance_history():
    """
    Endpoint to get attendance history for a user
    Query parameters:
    - sevarth_id: User's Sevarth ID
    - start_date: Optional start date (YYYY-MM-DD)
    - end_date: Optional end date (YYYY-MM-DD)
    """
    sevarth_id = request.args.get('sevarth_id')
    start_date = request.args.get('start_date')
    end_date = request.args.get('end_date')
    
    if not sevarth_id:
        return jsonify({'message': 'Missing required parameter: sevarth_id'}), 400
    
    try:
        # Query Firestore for attendance records
        query = db.collection('face-recognition-attendance').where('userId', '==', sevarth_id)
        
        if start_date:
            query = query.where('date', '>=', start_date)
        
        if end_date:
            query = query.where('date', '<=', end_date)
        
        # Order by date and time
        query = query.order_by('date', direction=firestore.Query.DESCENDING)
        query = query.order_by('time', direction=firestore.Query.DESCENDING)
        
        attendance_docs = query.get()
        
        # Format the results
        attendance_records = []
        for doc in attendance_docs:
            record = doc.to_dict()
            record['id'] = doc.id
            attendance_records.append(record)
        
        return jsonify({
            'attendance_records': attendance_records,
            'count': len(attendance_records)
        }), 200
        
    except Exception as e:
        logger.error(f"Error retrieving attendance history: {str(e)}")
        return jsonify({'message': f'Error retrieving attendance history: {str(e)}'}), 500

@app.route('/admin/users/<sevarth_id>', methods=['DELETE'])
@token_required
def delete_user(current_user, sevarth_id):
    try:
        # Check if user exists
        user_ref = db.collection('users').document(sevarth_id)
        user_doc = user_ref.get()
        
        if not user_doc.exists:
            return jsonify({
                'message': 'User not found',
                'success': False
            }), 404
        
        user_data = user_doc.to_dict()
        
        # Get the Firebase UID
        firebase_uid = user_data.get('uid')
        
        if not firebase_uid:
            return jsonify({
                'message': 'User data is incomplete',
                'success': False
            }), 400
        
        try:
            # Delete user from Firebase Authentication
            auth.delete_user(firebase_uid)
        except Exception as e:
            print(f"Error deleting user from Firebase Auth: {str(e)}")
            # Continue with Firestore deletion even if Firebase Auth deletion fails
        
        # Delete user document from Firestore
        user_ref.delete()
        
        # Delete user's face image if it exists
        try:
            image_path = f"reference_images/face_{sevarth_id}.jpg"
            blob = storage.bucket().blob(image_path)
            if blob.exists():
                blob.delete()
        except Exception as e:
            print(f"Error deleting user's face image: {str(e)}")
            # Continue even if face image deletion fails
        
        return jsonify({
            'message': 'User deleted successfully',
            'success': True
        })
        
    except Exception as e:
        print(f"Error deleting user: {str(e)}")
        return jsonify({
            'message': f'Failed to delete user: {str(e)}',
            'success': False
        }), 500

if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=True) 