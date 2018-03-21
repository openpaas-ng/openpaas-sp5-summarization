# -*- coding: utf-8 -*-
import re
import string
import codecs
import pandas as pd
import operator
import xmltodict


def read_ami_icsi(path):
    asr_output = pd.read_csv(
        path,
        sep='\t',
        header=None,
        names=['ID', 'start', 'end', 'letter', 'role', 'A', 'B', 'C', 'utt']
    )

    utterances = []
    for tmp in zip(asr_output['start'].tolist(), asr_output['end'].tolist(), asr_output['role'].tolist(), asr_output['utt'].tolist()):
        start, end, role, utt = tmp
        for ch in ['{vocalsound}', '{gap}', '{disfmarker}', '{comment}', '{pause}', '@reject@']:
            utt = re.sub(ch, '', utt)

        utt = re.sub("'Kay", 'Okay', utt)
        utt = re.sub("'kay", 'Okay', utt)
        utt = re.sub('"Okay"', 'Okay', utt)
        utt = re.sub("'cause", 'cause', utt)
        utt = re.sub("'Cause", 'cause', utt)
        utt = re.sub('"cause"', 'cause', utt)
        utt = re.sub('"\'em"', 'them', utt)
        utt = re.sub('"\'til"', 'until', utt)
        utt = re.sub('"\'s"', 's', utt)

        # l. c. d. -> lcd
        # t. v. -> tv
        utt = re.sub('h. t. m. l.', 'html', utt)
        utt = re.sub(r"(\w)\. (\w)\. (\w)\.", r"\1\2\3", utt)
        utt = re.sub(r"(\w)\. (\w)\.", r"\1\2", utt)
        utt = re.sub(r"(\w)\.", r"\1", utt)

        # strip extra white space
        utt = re.sub(' +', ' ', utt)
        # strip leading and trailing white space
        utt = utt.strip()

        if utt != '' and utt != '.' and utt != ' ':
            utterances.append({
                'start': start,
                'end': end,
                'role': role,
                'text': utt
            })

    # remove duplicate utterances per speaker
    # utterances = sorted(set(utterances), key=utterances.index)

    return utterances

def xml_to_dict(file, force_list=None):
    with codecs.open(file, encoding='utf-8') as f:
        doc = xmltodict.parse(f.read(), force_list=force_list)
    return doc

def read_cfpp2000(path):
    data = xml_to_dict(
        path, force_list=('annotatedU', 'div', 'annotationGrp')
    )

    try:
        annotatedUs = data['TEI']['text']['div']
    except:
        annotatedUs = data['TEI']['text']['body']['div']

    utterances = []
    for item in annotatedUs:
        if 'annotatedU' in item.keys():
            annotatedU = item['annotatedU']
        elif 'annotationGrp' in item.keys():
            annotatedU = item['annotationGrp']
        elif 'div' in item.keys():
            annotatedU = item['div'][0]['annotationBlock']
            if len(item['div']) != 1:
                raise RuntimeError()
        else:
            raise RuntimeError()

        for utt in annotatedU:
            if utt['u'] is None:
                continue
            if 'seg' not in utt['u'].keys():
                continue
            if type(utt['u']['seg']) is unicode:

                if '@wh' in utt.keys():
                    speaker = utt['@wh']
                elif '@who' in utt.keys():
                    speaker = utt['@who']
                else:
                    raise RuntimeError()
                utterances.append({
                    u'start': float(utt['@start'][2:]),
                    u'end': float(utt['@end'][2:]),
                    u'role': speaker,
                    u'text': ''.join([l for l in utt['u']['seg'] if l not in '/'])
                })

    return utterances
