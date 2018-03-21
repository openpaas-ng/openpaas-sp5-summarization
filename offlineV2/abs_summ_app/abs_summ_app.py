# -*- coding: utf-8 -*-
import os
import json
import unicodecsv
import codecs
import requests
import base64
import datetime
import utils
import dash
import flask
from StringIO import StringIO
import pandas as pd
import dash_core_components as dcc
import dash_html_components as html
from dash.dependencies import Input, Output, Event, State
from meeting.meeting_lists import ami_test_set, icsi_test_set, cfpp2000_set
from six.moves.urllib.parse import quote
from flask import send_from_directory
import visdcc

vertical_tab = True
path_to_root = os.path.dirname(os.path.abspath(__file__)) + '/'

app = dash.Dash()
app.config.update({
    # as the proxy server will remove the prefix
    'routes_pathname_prefix': '/abs_summ_app/',

    # the front-end will prefix this string to the requests
    # that are made to the proxy server
    'requests_pathname_prefix': '/abs_summ_app/'
})
server = app.server # Flask
app.title='Abstractive Meeting Summarization'

# if True Rendering dash apps offline
app.css.config.serve_locally = False
app.scripts.config.serve_locally = False

app.css.append_css({
    "external_url": "https://codepen.io/chriddyp/pen/bWLwgP.css"
})

app.css.append_css({
    "external_url": "https://codepen.io/chriddyp/pen/brPBPO.css"
})

app.css.append_css({
    "external_url": "http://datascience.open-paas.org/abs_summ_app/static/override.css"
})

app.layout = html.Div(className="container", children=[
    # load css
    # html.Link(
    #     rel='stylesheet',
    #     href='http://datascience.open-paas.org/abs_summ_app/static/bWLwgP.css'
    # ),
    #
    # html.Link(
    #     rel='stylesheet',
    #     href='http://datascience.open-paas.org/abs_summ_app/static/brPBPO.css'
    # ),
    #
    # html.Link(
    #     rel='stylesheet',
    #     href='http://datascience.open-paas.org/abs_summ_app/static/override.css'
    # ),



    html.Div(style={'textAlign': 'center', 'color': '#7FDBFF', 'backgroundColor': '#111111'}, children=[
        html.H4(
            children='Abstractive Meeting Summarization'
        )
    ]),

    html.Div(children=[
        html.Div(className="row", children=[
            html.Div(className="six columns", children=[
                html.Label('Select language'),
                dcc.Dropdown(
                    options=[
                        {'label': 'English', 'value': 'en'},
                        {'label': 'French', 'value': 'fr'}
                    ],
                    value='',
                    id='language'
                ),

                dcc.Tabs(
                    tabs=[
                        {'label': 'Select Built-in Data', 'value': 1},
                        {'label': 'Upload JSON Data', 'value': 2},
                        {'label': 'Paste A.S.R Data', 'value': 3},
                    ],
                    value=1,
                    id='data_tabs',
                    vertical=False
                ),

                html.Div(id='tab1-output', style={'display': 'none'}, children=[
                    html.Label('Select dataset'),
                    dcc.Dropdown(
                        options=[],
                        value='',
                        id='dataset'
                    ),

                    html.Label('Select meeting'),
                    dcc.Dropdown(
                        options=[],
                        value='',
                        id='meeting'
                    ),

                    html.Div(id='download-link-div', style={'display': 'none'}, children=[
                        html.A(
                            "Download transcription",
                            href='',
                            target="_blank",
                            download='',
                            id='download-link'
                        )
                    ])
                ]),

                html.Div(id='tab2-output', style={'display': 'none'}, children=[
                    dcc.Upload(
                        id='upload-data',
                        children=html.Div([
                            'Drag and Drop or ',
                            html.A('Select Files')
                        ]),
                        style={
                            'width': '100%',
                            'height': '60px',
                            'lineHeight': '60px',
                            'borderWidth': '1px',
                            'borderStyle': 'dashed',
                            'borderRadius': '5px',
                            'textAlign': 'center'
                        }
                    ),
                    html.A(
                        "Check example.json",
                        href='http://datascience.open-paas.org/abs_summ_app/static/example.json',
                        target="_blank"
                    ),

                    html.Div(id='show-uploaded-filename'),
                    html.Div(id='json-uploaded', style={'display': 'none'})
                ]),

                html.Div(id='tab3-output', style={'display': 'none'}, children=[
                    dcc.Textarea(
                        placeholder='Utterances',
                        value='',
                        style={'width': '100%', 'height': 'auto', 'resize': 'none'},
                        rows=10,
                        id='asr-pasted'
                    ),

                    html.A(
                        "Check example.txt",
                        href='http://datascience.open-paas.org/abs_summ_app/static/example.txt',
                        target="_blank"
                    )
                ])
            ]),
            html.Div(className="six columns", children=[
                html.Label('System name'),
                dcc.Dropdown(
                    options=[
                        {'label': 'Our Sytem', 'value': 'tixier'},
                        {'label': 'Our System (Baseline)', 'value': 'filippova'},
                        {'label': 'Our System (KeyRank)', 'value': 'boudin'},
                        {'label': 'Our System (FluCovRank)', 'value': 'mehdad'}
                    ],
                    value='tixier',
                    id='system_name'
                ),

                html.Label('Number of utterance communities'),
                dcc.Slider(
                    min=20,
                    max=60,
                    step=5,
                    value=50,
                    marks={i: str(i) for i in range(20, 65, 5)},
                    id='n_comms'
                ),

                html.Label('Minimum path length'),
                dcc.Slider(
                    min=6,
                    max=16,
                    step=2,
                    value=8,
                    marks={i: str(i) for i in range(6, 18, 2)},
                    id='minimum_path_length'
                ),

                html.Label('Scaling factor'),
                dcc.Slider(
                    min=0,
                    max=2,
                    step=0.1,
                    value=0.5,
                    marks={
                        0: '0',
                        0.1: '0.1',
                        0.2: '0.2',
                        0.3: '0.3',
                        0.4: '0.4',
                        0.5: '0.5',
                        0.6: '0.6',
                        0.7: '0.7',
                        0.8: '0.8',
                        0.9: '0.9',
                        1: '1',
                        1.1: '1.1',
                        1.2: '1.2',
                        1.3: '1.3',
                        1.4: '1.4',
                        1.5: '1.5',
                        1.6: '1.6',
                        1.7: '1.7',
                        1.8: '1.8',
                        1.9: '1.9',
                        2: '2'
                    },
                    id='scaling_factor'
                ),

                html.Label('Lambda'),
                dcc.Slider(
                    min=0,
                    max=1,
                    step=0.1,
                    value=0.7,
                    marks={
                        0: '0',
                        0.1: '0.1',
                        0.2: '0.2',
                        0.3: '0.3',
                        0.4: '0.4',
                        0.5: '0.5',
                        0.6: '0.6',
                        0.7: '0.7',
                        0.8: '0.8',
                        0.9: '0.9',
                        1: '1'
                    },
                    id='lambda'
                ),

                html.Label('Summary size in words (budget)'),
                dcc.Slider(
                    min=50,
                    max=500,
                    step=50,
                    value=100,
                    marks={i: str(i) for i in range(50, 550, 50)},
                    id='budget'
                )
            ])
        ])
    ]),

    html.Button(id='submit-button', n_clicks=0, children='Submit', className='button-primary', style={'width': '100%'}),

    # Hidden div inside the app that stores the intermediate value
    html.Div(id='intermediate-value', style={'display': 'none'}),

    html.Div(children=[
        html.H6(
            children='Summaries:'
        ),

        html.Div(className="row", children=[
            dcc.Textarea(
                placeholder='System',
                value='',
                style={'height': 'auto', 'resize': 'none'},
                rows=15,
                readOnly=True,
                id='system',
                className="six columns"
            ),
            dcc.Textarea(
                placeholder='Human',
                value='',
                style={'height': 'auto', 'resize': 'none'},
                rows=15,
                readOnly=True,
                id='human',
                className="six columns"
            )
        ])
    ]),
    html.Div(id='mscg', style={'display': 'none'}, children=[
        html.H6(
            children='Multi-Sentence Compression Graph:'
        ),

        dcc.Tabs(
            tabs=[],
            value=0,
            id='tabs',
            vertical=False
        ),

        visdcc.Network(
            id='net',
            options=dict(height='600px', width='100%')
        ),

        html.Div(style={'whiteSpace': 'pre-line', 'textAlign': 'center'}, children=[
            dcc.Markdown(id='net_text')
        ])
    ]),

    html.Hr()

])

###########################################
@app.server.route('/static/<path:path>')
def static_file(path):
    return send_from_directory(path_to_root + 'static', path)
###########################################
@app.callback(Output('tab1-output', 'style'), [Input('data_tabs', 'value')])
def display_content(value):
    if value == 1:
        return {}
    else:
        return {'display': 'none'}

@app.callback(Output('tab2-output', 'style'), [Input('data_tabs', 'value')])
def display_content(value):
    if value == 2:
        return {}
    else:
        return {'display': 'none'}

@app.callback(Output('tab3-output', 'style'), [Input('data_tabs', 'value')])
def display_content(value):
    if value == 3:
        return {}
    else:
        return {'display': 'none'}
###########################################
@app.callback(Output('json-uploaded', 'children'),
              [Input('upload-data', 'contents')])
def update_output(contents):
    if contents is not None:
        return contents

@app.callback(Output('show-uploaded-filename', 'children'),
              [Input('upload-data', 'filename'),
               Input('upload-data', 'last_modified')])
def update_output(filename, last_modified):
    if filename is not None:
        last_modified_time = datetime.datetime.fromtimestamp(int(last_modified)).strftime('%Y-%m-%d %H:%M:%S')
        return 'Uploaded! file name: ' + filename + ', last modified time: '+ last_modified_time
###########################################
@app.callback(
    Output(component_id='dataset', component_property='options'),
    [Input(component_id='language', component_property='value')]
)
def update_options(language):
    if language == 'en':
        return [
            {'label': 'AMI', 'value': 'ami'},
            {'label': 'ICSI', 'value': 'icsi'}
        ]
    elif language == 'fr':
        return [
            {'label': 'CFPP2000', 'value': 'cfpp2000'}
        ]
    else:
        return []

@app.callback(
    Output(component_id='meeting', component_property='options'),
    [Input(component_id='dataset', component_property='value')]
)
def update_options(dataset):
    if dataset == 'ami':
        return [
            {'label': meeting_id, 'value': meeting_id}
            for meeting_id in ami_test_set
        ]
    elif dataset == 'icsi':
        return [
            {'label': meeting_id, 'value': meeting_id}
            for meeting_id in icsi_test_set
        ]
    elif dataset == 'cfpp2000':
        return [
            {'label': meeting_id, 'value': meeting_id}
            for meeting_id in cfpp2000_set
        ]
    else:
        return []

##########################################
@app.callback(
    Output(component_id='download-link-div', component_property='style'),
    [Input(component_id='meeting', component_property='value')]
)
def update_options(meeting):
    if meeting != '':
        return {}
    else:
        return {'display': 'none'}

@app.callback(
    Output(component_id='download-link', component_property='download'),
    [Input(component_id='meeting', component_property='value')],
    [State('dataset', 'value')]
)
def update_options(meeting, dataset):
    if meeting != '':
        return dataset + '-' + meeting + '.csv'
    else:
        return ''

@app.callback(
    Output(component_id='download-link', component_property='href'),
    [Input(component_id='meeting', component_property='value')],
    [State('dataset', 'value')]
)
def update_options(meeting, dataset):
    if meeting != '':
        if dataset == 'ami' or dataset == 'icsi':
            path = path_to_root + 'meeting/' + dataset + '/' + meeting + '.da-asr'
            transcription = utils.read_ami_icsi(path)
        elif dataset == 'cfpp2000':
            path = path_to_root + 'meeting/' + dataset + '/' + meeting + '.teiml'
            transcription = utils.read_cfpp2000(path)
        else:
            raise RuntimeError()
        df = pd.DataFrame(transcription)
        return "data:text/csv;charset=utf-8," + quote(df.to_csv(index=False, encoding='utf-8', columns=['start', 'end', 'role', 'text']))
    else:
        return ''
###########################################
@app.callback(Output('intermediate-value', 'children'),
              [Input('submit-button', 'n_clicks')],
              [State('language', 'value'),
               State('data_tabs', 'value'),
               State('dataset', 'value'),
               State('meeting', 'value'),
               State('json-uploaded', 'children'),
               State('asr-pasted', 'value'),
               State('system_name', 'value'),
               State('n_comms', 'value'),
               State('minimum_path_length', 'value'),
               State('scaling_factor', 'value'),
               State('lambda', 'value'),
               State('budget', 'value')
               ])
def update_output(n_clicks, language, data_tabs, dataset, meeting, json_uploaded, asr_pasted, system_name, n_comms, minimum_path_length, scaling_factor, lamda, budget):
    if n_clicks == 0:
        return ''

    if data_tabs == 1:
        if dataset == 'ami' or dataset == 'icsi':
            path = path_to_root + 'meeting/' + dataset + '/' + meeting + '.da-asr'
            transcription = utils.read_ami_icsi(path)
        elif dataset == 'cfpp2000':
            path = path_to_root + 'meeting/' + dataset + '/' + meeting + '.teiml'
            transcription = utils.read_cfpp2000(path)
        else:
            raise RuntimeError()
    elif data_tabs == 2:
        content_type, content_string = json_uploaded.split(',')
        decoded = base64.b64decode(content_string)
        transcription = json.loads(decoded)
    elif data_tabs == 3:
        f = StringIO(asr_pasted.encode('utf-8'))
        transcription = [
            {k: v for k, v in row.items()}
             for row in unicodecsv.DictReader(f, skipinitialspace=True, delimiter='\t', encoding='utf-8')
        ]
    else:
        return ''

    post_data = {
        'language': language,
        'summary_size': budget,
        'transcription': transcription,

        'system_name': system_name,
        'n_comms': n_comms,
        'minimum_path_length': minimum_path_length,
        'scaling_factor': scaling_factor,
        'lambda': lamda
    }

    r = requests.post(
        'http://datascience.open-paas.org/abs_summ/api_app',
        data=json.dumps(post_data),
        headers={'Content-type': 'application/json'}
    ).json()

    return json.dumps(r)

@app.callback(Output('system', 'value'),
              [Input('intermediate-value', 'children')],
              [State('submit-button', 'n_clicks')]
              )
def update_output(intermediate_value, n_clicks):
    if n_clicks == 0:
        return ''

    return '\n'.join(
        json.loads(intermediate_value)['summary']
    )
###########################################
@app.callback(Output('human', 'value'),
              [Input('system', 'value')],
              [State('submit-button', 'n_clicks'),
               State('data_tabs', 'value'),
               State('dataset', 'value'),
               State('meeting', 'value'),
               ])
def update_output(system, n_clicks, data_tabs, dataset, meeting):
    if n_clicks == 0:
        return ''

    if data_tabs != 1:
        return ''

    if dataset == 'ami':
        path = path_to_root + 'meeting/' + dataset + '/' + meeting + '.longabstract'
    elif dataset == 'icsi':
        path = path_to_root + 'meeting/' + dataset + '/' + meeting + '.ducref.longabstract'
    else:
        return ''

    with codecs.open(path, 'r', 'utf-8') as f:
        return '\n'.join(
            f.read().splitlines()
        )
###########################################
@app.callback(Output('mscg', 'style'),
              [Input('intermediate-value', 'children')],
              [State('submit-button', 'n_clicks')]
              )
def update_output(intermediate_value, n_clicks):
    if n_clicks == 0:
        return {'display': 'none'}
    return {}


@app.callback(Output('tabs', 'tabs'),
              [Input('intermediate-value', 'children')],
              [State('submit-button', 'n_clicks'),
               State('n_comms', 'value')]
              )
def update_output(intermediate_value, n_clicks, n_comms):
    if n_clicks == 0:
        return []

    return [
        {'label': str(i), 'value': i}
        for i in range(n_comms)
    ]


@app.callback(Output('net_text', 'children'),
              [Input('tabs', 'value')],
              [
                  State('intermediate-value', 'children'),
                  State('submit-button', 'n_clicks')
              ]
              )
def update_output(index_community, intermediate_value, n_clicks):
    if n_clicks == 0:
        return ''

    compression = u'**' + json.loads(intermediate_value)['compressions'][index_community] + u'**'
    community = u'\n> ⚫ ' + u'\n⚫ '.join(
        json.loads(intermediate_value)['communities'][index_community]
    )

    return compression + community

@app.callback(Output('net', 'data'),
              [Input('tabs', 'value')],
              [
                  State('intermediate-value', 'children'),
                  State('submit-button', 'n_clicks')
              ]
              )
def update_output_net(index_community, intermediate_value, n_clicks):
    if n_clicks == 0:
        return {
            'nodes': [
                {'id': 1, 'label': 'This', 'shape': 'box'},
                {'id': 2, 'label': 'is', 'shape': 'box'},
                {'id': 3, 'label': 'a', 'shape': 'box'},
                {'id': 4, 'label': 'sample', 'shape': 'box'},
                {'id': 5, 'label': 'graph', 'shape': 'box'},
                {'id': 6, 'label': ',', 'shape': 'box'},
                {'id': 7, 'label': 'please', 'shape': 'box'},
                {'id': 8, 'label': 'click', 'shape': 'box'},
                {'id': 9, 'label': 'a', 'shape': 'box'},
                {'id': 10, 'label': 'tab', 'shape': 'box'},
                {'id': 11, 'label': '.', 'shape': 'box'}
            ],
            'edges': [
               {'id':str(i) + '-' + str(j), 'from': i, 'to': j, 'arrows': 'to'}
                for i, j in zip(range(1, 11), range(2, 12))
            ]
        }

    graph = json.loads(intermediate_value)['graphs'][index_community]

    def get_node_id(node):
        return node[0] + ', ' + str(node[1])

    def get_edge_id(edge):
        return get_node_id(edge[0]) + '->' + get_node_id(edge[1])

    def get_node_color(node):
        pos_tag = node[0].split('/-/')[1]
        if pos_tag.startswith('V'):
            return '#f4d4e3'
        elif pos_tag.startswith('N'):
            return '#0ABDA0'
        elif pos_tag.startswith('J') or pos_tag == 'ADJ':
            return '#EBF2EA'
        elif pos_tag.startswith('R') or pos_tag == 'ADV':
            return '#D4DCA9'
        elif pos_tag.startswith('D'):
            return '#BF9D7A'
        elif pos_tag.startswith('I') or pos_tag == 'P':
            return '#BF9D7A'
        elif pos_tag.startswith('-'):
            return '#b36154'
        else:
            return '#80ADD7'


    nodes = [
        {'id': get_node_id(node), 'label': get_node_id(node), 'shape': 'box', 'color': get_node_color(node)}
        for node in graph['nodes']
    ]

    edges = [
        {'id': get_edge_id(edge), 'from': get_node_id(edge[0]), 'to': get_node_id(edge[1]), 'arrows': 'to'}
        for edge in graph['edges']
    ]

    data = {'nodes':nodes, 'edges':edges}

    return data

###########################################

if __name__ == '__main__':
    app.run_server()
