import functions_framework
from firebase_admin import initialize_app, firestore
from datetime import datetime
import logging

# Initialize Firebase Admin
initialize_app()
db = firestore.client()

# HTTP trigger for manual testing
@functions_framework.http
def generate_stories_manual(request):
    """HTTP Cloud Function for manual story generation"""
    # Simple auth check
    auth_key = request.args.get('key', '')
    if auth_key != 'test-key-123':
        return 'Forbidden', 403
    
    try:
        result = generate_stories()
        return f'Success! Generated {result} stories', 200
    except Exception as e:
        logging.error(f'Error generating stories: {e}')
        return f'Error: {str(e)}', 500

# Scheduled function - we'll set this up after testing
def generate_stories():
    """Generate daily stories"""
    today = datetime.now().strftime('%Y-%m-%d')
    logging.info(f'Generating stories for {today}')
    
    # Story templates for now
    story_templates = [
        {
            'title': 'The Crystal Cave',
            'content': 'Deep in the mountains, a cave filled with singing crystals awaits discovery...',
            'dayIndex': 0,
            'publishDate': today
        },
        {
            'title': 'Merchant of Dreams',
            'content': 'In the night market, a mysterious vendor sells dreams in glass bottles...',
            'dayIndex': 1,
            'publishDate': today
        },
        {
            'title': 'The Last Library',
            'content': 'When all digital records failed, one ancient library held humanity\'s hope...',
            'dayIndex': 2,
            'publishDate': today
        },
        {
            'title': 'Stars Whisper',
            'content': 'The astronomer heard voices in the static between stars...',
            'dayIndex': 3,
            'publishDate': today
        },
        {
            'title': 'The Phoenix Gate',
            'content': 'Every thousand years, the gate opens for one worthy soul...',
            'dayIndex': 4,
            'publishDate': today
        }
    ]
    
    # Add stories to Firestore
    batch = db.batch()
    for story in story_templates:
        doc_ref = db.collection('stories').document()
        batch.set(doc_ref, story)
    
    batch.commit()
    return len(story_templates)