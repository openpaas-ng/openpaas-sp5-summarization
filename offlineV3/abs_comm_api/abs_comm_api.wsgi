activate_this = '/data/www/abs_comm_api/resource/venv/bin/activate_this.py'
with open(activate_this) as file_:
    exec(file_.read(), dict(__file__=activate_this))

import sys

sys.path.insert(0, '/data/www/abs_comm_api')

from abs_comm_api import app as application
