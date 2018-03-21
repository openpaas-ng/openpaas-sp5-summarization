activate_this = '/data/www/abs_summ_app/resources/venv/bin/activate_this.py'
execfile(activate_this, dict(__file__=activate_this))

import sys

sys.path.insert(0, '/data/www/abs_summ_app')

from abs_summ_app import server as application
