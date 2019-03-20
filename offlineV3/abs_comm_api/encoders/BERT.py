import json
import keras
import keras.backend as K
import tensorflow as tf
from keras.layers import Masking
from keras_bert.bert import gelu, get_encoders
from keras_bert.layers.embedding import TokenEmbedding, PositionEmbedding, LayerNormalization

def load_trained_bert_from_checkpoint(config_file,
                                       checkpoint_file,
                                       trainable=False,
                                       seq_len=None,
                                       num_hidden_layers=None):
    """Load trained official model from checkpoint.

    :param config_file: The path to the JSON configuration file.
    :param checkpoint_file: The path to the checkpoint files, should end with '.ckpt'.
    :param trainable: If trainable, the whole model will be returned.
                     Otherwise, weights are fixed.
    :param seq_len: If it is not None and it is shorter than the value in the config file, the weights in position embeddings will be sliced to fit the new length.
    :return:
    """
    with open(config_file, 'r') as reader:
        config = json.loads(reader.read())

    if seq_len is None:
        seq_len = config['max_position_embeddings']
    else:
        seq_len = min(seq_len, config['max_position_embeddings'])

    if num_hidden_layers is None:
        num_hidden_layers = config['num_hidden_layers']
    else:
        num_hidden_layers = min(num_hidden_layers, config['num_hidden_layers'])

    model = get_model(
        token_num=config['vocab_size'],
        pos_num=seq_len,
        seq_len=seq_len,
        embed_dim=config['hidden_size'],
        transformer_num=num_hidden_layers,
        head_num=config['num_attention_heads'],
        feed_forward_dim=config['intermediate_size'],
        trainable=trainable,
    )

    segment_embedding_of_index_zero = tf.train.load_variable(checkpoint_file, 'bert/embeddings/token_type_embeddings')[0]

    model.get_layer(name='Embedding-Token').set_weights([
        segment_embedding_of_index_zero + tf.train.load_variable(checkpoint_file, 'bert/embeddings/word_embeddings'),
    ])
    model.get_layer(name='Embedding-Position').set_weights([
        tf.train.load_variable(checkpoint_file, 'bert/embeddings/position_embeddings')[:seq_len, :],
    ])
    model.get_layer(name='Embedding-Norm').set_weights([
        tf.train.load_variable(checkpoint_file, 'bert/embeddings/LayerNorm/gamma'),
        tf.train.load_variable(checkpoint_file, 'bert/embeddings/LayerNorm/beta'),
    ])
    for i in range(num_hidden_layers):
        model.get_layer(name='Encoder-%d-MultiHeadSelfAttention' % (i + 1)).set_weights([
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/self/query/kernel' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/self/query/bias' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/self/key/kernel' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/self/key/bias' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/self/value/kernel' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/self/value/bias' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/output/dense/kernel' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/output/dense/bias' % i),
        ])
        model.get_layer(name='Encoder-%d-MultiHeadSelfAttention-Norm' % (i + 1)).set_weights([
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/output/LayerNorm/gamma' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/output/LayerNorm/beta' % i),
        ])
        model.get_layer(name='Encoder-%d-MultiHeadSelfAttention-Norm' % (i + 1)).set_weights([
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/output/LayerNorm/gamma' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/attention/output/LayerNorm/beta' % i),
        ])
        model.get_layer(name='Encoder-%d-FeedForward' % (i + 1)).set_weights([
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/intermediate/dense/kernel' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/intermediate/dense/bias' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/output/dense/kernel' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/output/dense/bias' % i),
        ])
        model.get_layer(name='Encoder-%d-FeedForward-Norm' % (i + 1)).set_weights([
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/output/LayerNorm/gamma' % i),
            tf.train.load_variable(checkpoint_file, 'bert/encoder/layer_%d/output/LayerNorm/beta' % i),
        ])

    return model


def get_model(token_num,
              pos_num=512,
              seq_len=512,
              embed_dim=768,
              transformer_num=12,
              head_num=12,
              feed_forward_dim=3072,
              dropout_rate=0.1,
              attention_activation=None,
              feed_forward_activation=gelu,
              custom_layers=None,
              trainable=True):
    """Get BERT model.

    See: https://arxiv.org/pdf/1810.04805.pdf

    :param token_num: Number of tokens.
    :param pos_num: Maximum position.
    :param seq_len: Maximum length of the input sequence or None.
    :param embed_dim: Dimensions of embeddings.
    :param transformer_num: Number of transformers.
    :param head_num: Number of heads in multi-head attention in each transformer.
    :param feed_forward_dim: Dimension of the feed forward layer in each transformer.
    :param dropout_rate: Dropout rate.
    :param attention_activation: Activation for attention layers.
    :param feed_forward_activation: Activation for feed-forward layers.
    :param custom_layers: A function that takes the embedding tensor and returns the tensor after feature extraction.
                          Arguments such as `transformer_num` and `head_num` will be ignored if `custom_layer` is not
                          `None`.
    :param trainable: The built model will be returned if it is `True`, otherwise weights are fixed.
    :return: The compiled model.
    """
    token_input = keras.layers.Input(
        shape=(seq_len,),
        name='Input-Token',
    )

    embed_layer, embed_weights = get_embedding(
        token_input,
        token_num=token_num,
        embed_dim=embed_dim,
        pos_num=pos_num,
        dropout_rate=dropout_rate,
        trainable=trainable,
    )

    transformed = embed_layer
    if custom_layers is not None:
        kwargs = {}
        if keras.utils.generic_utils.has_arg(custom_layers, 'trainable'):
            kwargs['trainable'] = trainable
        transformed = custom_layers(transformed, **kwargs)
    else:
        transformed = get_encoders(
            encoder_num=transformer_num,
            input_layer=transformed,
            head_num=head_num,
            hidden_dim=feed_forward_dim,
            attention_activation=attention_activation,
            feed_forward_activation=feed_forward_activation,
            dropout_rate=dropout_rate,
            trainable=trainable,
        )

    def mask_pad(x):
        # mask output of [PAD] to zero vectors
        i, o = x
        boolean_mask = K.not_equal(i, 0)
        boolean_mask = K.cast(boolean_mask, K.dtype(o))
        return o * K.expand_dims(boolean_mask, axis=-1)

    transformed = keras.layers.Lambda(mask_pad)([token_input, transformed])
    transformed = Masking(mask_value=0.)(transformed)

    model = keras.models.Model(inputs=token_input, outputs=transformed)

    return model


def get_embedding(token_input, token_num, pos_num, embed_dim, dropout_rate=0.1, trainable=True):
    """Get embedding layer.

    See: https://arxiv.org/pdf/1810.04805.pdf

    :param inputs: Input layers.
    :param token_num: Number of tokens.
    :param pos_num: Maximum position.
    :param embed_dim: The dimension of all embedding layers.
    :param dropout_rate: Dropout rate.
    :param trainable: Whether the layers are trainable.
    :return: The merged embedding layer and weights of token embedding.
    """
    token_segment_embeddings = TokenEmbedding(
        input_dim=token_num,
        output_dim=embed_dim,
        mask_zero=True,
        trainable=trainable,
        name='Embedding-Token',
    )(token_input)

    embed_layer, embed_weights = token_segment_embeddings

    embed_layer = PositionEmbedding(
        input_dim=pos_num,
        output_dim=embed_dim,
        mode=PositionEmbedding.MODE_ADD,
        trainable=trainable,
        name='Embedding-Position',
    )(embed_layer)
    if dropout_rate > 0.0:
        dropout_layer = keras.layers.Dropout(
            rate=dropout_rate,
            name='Embedding-Dropout',
        )(embed_layer)
    else:
        dropout_layer = embed_layer
    norm_layer = LayerNormalization(
        trainable=trainable,
        name='Embedding-Norm',
    )(dropout_layer)
    return norm_layer, embed_weights
