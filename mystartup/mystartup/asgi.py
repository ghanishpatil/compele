"""
ASGI config for mystartup project.
"""

import os

from django.core.asgi import get_asgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'mystartup.settings')

application = get_asgi_application() 