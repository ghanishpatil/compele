"""mystartup URL Configuration"""
from django.contrib import admin
from django.urls import path
from django.http import HttpResponse

def index(request):
    return HttpResponse("<h1>Welcome to MyStartup!</h1>")

urlpatterns = [
    path('admin/', admin.site.urls),
    path('', index, name='index'),
] 