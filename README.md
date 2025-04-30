# MyStartup 2.0

A Django web application ready for deployment on Render.

## Local Development

1. Create a virtual environment:
```
python -m venv venv
venv\Scripts\activate  # On Windows
source venv/bin/activate  # On Linux/Mac
```

2. Install dependencies:
```
pip install -r requirements.txt
```

3. Run migrations:
```
python manage.py migrate
```

4. Start the development server:
```
python manage.py runserver
```

## Deployment on Render

This application is configured for deployment on Render. It uses:

- Gunicorn as the WSGI server
- WhiteNoise for serving static files
- dj-database-url for database configuration

### Instructions for Deployment on Render

1. Push your code to GitHub
2. Create a new Web Service on Render
3. Connect your GitHub repository
4. Set the build command to: `pip install -r requirements.txt && python manage.py collectstatic --noinput && python manage.py migrate`
5. Set the start command to: `gunicorn mystartup.wsgi:application`
6. Add environment variables:
   - `SECRET_KEY` (generate a secure random key)
   - `DATABASE_URL` (if using a database)

## Features

- Ready for deployment on Render
- Static file handling with WhiteNoise
- Database configuration for deployment 