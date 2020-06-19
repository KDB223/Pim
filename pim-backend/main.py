import firebase_admin
import json
from firebase_admin import credentials, db

import model

# Replace with your key
PATH_TO_SAC_KEY = './ServiceAccountKey.json'

cred = credentials.Certificate(PATH_TO_SAC_KEY)

sac = open(PATH_TO_SAC_KEY, 'r')
project_id = json.load(sac)["project_id"]

pim_app = firebase_admin.initialize_app(cred, {
    'databaseURL': 'https://' + project_id + '.firebaseio.com/'
})
ref = db.reference('rooms')

# Callback function called by model.py after performing inference
def on_result(path, distacting):
    if distacting:
        print('Content is distracting. Updating db...')
        db.reference('rooms' + path).child('distracting').set(True)
        print('Updated ' + path)
    else:
        print('Not distracting')

# Listener function, invoked when database is written to
def msg_listener(event):
    print('Listener fired')
    if not isinstance(event.data, bool):
        try:
            content = event.data.get('content')
            if content is not None:
                print('Found ' + content)
                print('Checking...')
                model.is_distracting(content, event.path, on_result)
        except Exception as e:
            print(e.args)
            print(e)


print('Starting listener')
ref.listen(msg_listener)
print('Listener running')
