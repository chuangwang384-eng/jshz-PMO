"""HTTP checks for synthetic dashboard fixtures on the dedicated loopback app only.
Set DASHBOARD_TEST_PASSWORD; optionally DASHBOARD_TEST_URL (localhost:18085 only).
No production accounts, data writes or credentials are stored in this script.
"""
import json
import os
from urllib.parse import urlparse
import requests

base = os.environ.get('DASHBOARD_TEST_URL', 'http://127.0.0.1:18085')
url = urlparse(base)
assert url.scheme == 'http' and url.hostname in ('127.0.0.1', 'localhost') and url.port == 18085
password = os.environ['DASHBOARD_TEST_PASSWORD']
report = []

def check(label, condition):
    assert condition, label
    report.append(label)

def login(name):
    client = requests.Session()
    response = client.post(base + '/api/auth/login', json={'username': name, 'password': password}, timeout=5)
    check(name + ' login', response.json().get('code') == 0)
    return client

def get(client, path, code=0):
    response = client.get(base + path, timeout=5)
    payload = response.json()
    check(path + ' code=' + str(code), payload.get('code') == code)
    return payload.get('data')

check('unauthenticated overview rejected', requests.get(base + '/api/dashboard/overview', timeout=5).status_code == 401)
a = login('dashboard-fixture-a')
b = login('dashboard-fixture-b')
first = get(a, '/api/dashboard/overview')
check('all overview sections distinguish errors from empty', all(section['status'] != 'error' for section in first['sections'].values()))
second = get(b, '/api/dashboard/overview?userId=90001')
check('employee does not receive approval count', 'myApprovals' not in second['sections'])
check('forged identity cannot read another users favorites', second['sections']['favorites']['data']['total'] == 0)
get(b, '/api/dashboard/focus/application/90002', 403)
get(b, '/api/dashboard/focus/notification/90001', 404)
get(b, '/api/dashboard/my-approvals', 403)
get(a, '/api/dashboard/overview?sections=favorites,favorites', 400)
get(a, '/api/search/global?keyword=x&modules=expert,expert', 400)
get(a, '/api/search/global?keyword=' + 'a' * 101, 400)
get(a, '/api/search/global?keyword=%20', 400)
results = get(a, '/api/search/global?keyword=工作台')
check('six permitted groups are returned', len(results['groups']) == 6)
for group in results['groups']:
    check(group['module'] + ' successful', group['status'] in ('success', 'empty'))
    full = get(a, '/api/search/global/module/' + group['module'] + '?keyword=工作台')
    check(group['module'] + ' full list shares preview count', full['total'] == group['total'])
    check(group['module'] + ' preview limited to five', len(group['items']) <= 5)
    check(group['module'] + ' no sensitive fields', all(set(item) <= {'id', 'title', 'extra', 'target'} for item in group['items']))
print(json.dumps({'result': 'PASS', 'checks': report}, ensure_ascii=False, indent=2))
