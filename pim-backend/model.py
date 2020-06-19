import pickle
import re


def is_distracting(msg, path, callback):
    model = pickle.load(open('model.sav', 'rb'))
    
    urls = find_urls(msg)
    print(urls)
    for s in urls:
        msg = msg.replace(s, "")
    
    result = model.predict(['', msg.replace("\n", " ")])
    if result[1] == 0:
        callback(path, True)     # Distracting
    else:
        callback(path, False)    # Not distracting
        
def find_urls(string):
    regex = r"(?i)\b((?:https?://|www\d{0,3}[.]|[a-z0-9.\-]+[.][a-z]{2,4}/)(?:[^\s()<>]+|\(([^\s()<>]+|(\([^\s()<>]+\)))*\))+(?:\(([^\s()<>]+|(\([^\s()<>]+\)))*\)|[^\s`!()\[\]{};:'\".,<>?]))"
    url = re.findall(regex,string)       
    return [x[0] for x in url] 

