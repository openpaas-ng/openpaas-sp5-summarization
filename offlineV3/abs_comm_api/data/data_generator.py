import numpy as np
from keras.utils import Sequence
from data.data_generation import generate_tuples, get_X_Y

class DataGenerator(Sequence):

    def __init__(self, path_to_summlink, utterances, n_tuple, meeting_list, pre_context_size, post_context_size, max_sequence_length, with_extra_features, batch_size):
        'Initialization'
        self.path_to_summlink = path_to_summlink
        self.utterances = utterances
        self.n_tuple = n_tuple
        self.meeting_list = meeting_list
        self.pre_context_size = pre_context_size
        self.post_context_size = post_context_size

        self.max_sequence_length = max_sequence_length
        self.with_extra_features = with_extra_features

        self.batch_size = batch_size

        self.on_epoch_end()

    def __len__(self):
        'Denotes the number of batches per epoch'
        return int(np.floor(len(self.Y) / self.batch_size))

    def __getitem__(self, index):
        'Generate one batch of data'
        # Generate indexes of the batch
        indexes = range(index*self.batch_size, (index+1)*self.batch_size)

        return [x[indexes] for x in self.X], self.Y[indexes]

    def on_epoch_end(self):
        print('\non_epoch_end: generate tuples and create data')
        tuples = generate_tuples(
            self.path_to_summlink,
            self.utterances,
            self.n_tuple,
            self.meeting_list,
            self.pre_context_size,
            self.post_context_size,
            training=True
        )

        self.X, self.Y = get_X_Y(self.n_tuple, tuples, self.utterances, self.max_sequence_length, self.with_extra_features)
