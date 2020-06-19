import threading

import firebase_admin
from firebase_admin import credentials, db

import model

cred = credentials.Certificate('./ServiceAccountKey.json')
pim_app = firebase_admin.initialize_app(cred, {
    'databaseURL': 'https://productiveim-e40d4.firebaseio.com/'
})
ref = db.reference('rooms')


def on_result(path, distacting):
    if distacting:
        print('Content is distracting. Updating db...')
        db.reference('rooms' + path).child('distracting').set(True)
        print('Updated ' + path)
    else:
        print('Not distracting')


def msg_listener(event):
    print('Listener fired')
    if not isinstance(event.data, bool):
        try:
            content = event.data.get('content')
            if content is not None:
                print('Found ' + content)
                print('Checking...')
                model.is_distracting(content, event.path, on_result)
                # threading.Thread(target=model.is_distracting, args=(content, event.path, on_result,))
                # if model.is_distracting(content, event.path, on_result):
                #    print('Content is distracting. Updating db...')
                #    db.reference('rooms' + event.path).child('distracting').set(True)
                #    print('Updated ' + event.path)
                # else:
                #    print('Not distracting')
        except Exception as e:
            print(e.args)
            print(e)


print('Starting listener')
ref.listen(msg_listener)
print('Listener running')
