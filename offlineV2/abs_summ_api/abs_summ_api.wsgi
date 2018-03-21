activate_this = '/data/www/abs_summ_api/resources/venv/bin/activate_this.py'
execfile(activate_this, dict(__file__=activate_this))

import sys

sys.path.insert(0, '/data/www/abs_summ_api')

from abs_summ_api import app as application
