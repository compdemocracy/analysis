import numba
import numpy as np

@numba.njit()
def sparsity_aware_dist(a, b):
    n_both_seen = len(a) - (np.isnan(a) | np.isnan(b)).sum()
    return (n_both_seen - (a == b).sum() + 1) / (n_both_seen + 2)

@numba.njit()
def sparsity_aware_dist2(a, b):
    definitive_votes = (a != 0) | (b != 0)
    n_both_seen = definitive_votes.sum()
    return (n_both_seen - (definitive_votes & (a == b)).sum() + 1) / (n_both_seen + 1)
