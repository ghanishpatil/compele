"""
WSGI config for mystartup project.
"""

import os

from django.core.wsgi import get_wsgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'mystartup.settings')

application = get_wsgi_application() 