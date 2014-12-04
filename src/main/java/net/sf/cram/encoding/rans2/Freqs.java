package net.sf.cram.encoding.rans2;

import java.nio.ByteBuffer;
import java.util.Arrays;

import net.sf.cram.encoding.rans2.Decoding.FC;
import net.sf.cram.encoding.rans2.Decoding.RansDecSymbol;
import net.sf.cram.encoding.rans2.Decoding.ari_decoder;
import net.sf.cram.encoding.rans2.Encoding.RansEncSymbol;

class Freqs {

	static void readStats_o0(ByteBuffer cp, ari_decoder D, RansDecSymbol[] syms) {
		// Precompute reverse lookup of frequency.
		int rle = 0;
		int x = 0;
		int j = cp.get() & 0xFF;
		do {
			if (D.fc[j] == null)
				D.fc[j] = new Decoding.FC();
			if ((D.fc[j].F = (cp.get() & 0xFF)) >= 128) {
				D.fc[j].F &= ~128;
				D.fc[j].F = ((D.fc[j].F & 127) << 8) | (cp.get() & 0xFF);
			}
			D.fc[j].C = x;

			Decoding.RansDecSymbolInit(syms[j], D.fc[j].C, D.fc[j].F);

			/* Build reverse lookup table */
			if (D.R == null)
				D.R = new byte[Constants.TOTFREQ];
			Arrays.fill(D.R, x, x + D.fc[j].F, (byte) j);

			x += D.fc[j].F;

			if (rle == 0 && j + 1 == (0xFF & cp.get(cp.position()))) {
				j = cp.get() & 0xFF;
				rle = cp.get() & 0xFF;
			} else if (rle != 0) {
				rle--;
				j++;
			} else {
				j = cp.get() & 0xFF;
			}
		} while (j != 0);

		assert (x < Constants.TOTFREQ);
	}

	static void readStats_o1(ByteBuffer cp, ari_decoder[] D,
			RansDecSymbol[][] syms) {
		int rle_i = 0;
		int i = 0xFF & cp.get();
		do {
			int rle_j = 0;
			int x = 0;
			int j = 0xFF & cp.get();
			if (D[i] == null)
				D[i] = new ari_decoder();
			do {
				if (D[i].fc[j] == null)
					D[i].fc[j] = new FC();
				if ((D[i].fc[j].F = (0xFF & cp.get())) >= 128) {
					D[i].fc[j].F &= ~128;
					D[i].fc[j].F = ((D[i].fc[j].F & 127) << 8)
							| (0xFF & cp.get());
				}
				D[i].fc[j].C = x;

				if (D[i].fc[j].F == 0)
					D[i].fc[j].F = Constants.TOTFREQ;

				if (syms[i][j] == null)
					syms[i][j] = new RansDecSymbol();

				Decoding.RansDecSymbolInit(syms[i][j], D[i].fc[j].C,
						D[i].fc[j].F);

				/* Build reverse lookup table */
				if (D[i].R == null)
					D[i].R = new byte[Constants.TOTFREQ];
				Arrays.fill(D[i].R, x, x + D[i].fc[j].F, (byte) j);

				x += D[i].fc[j].F;
				assert (x <= Constants.TOTFREQ);

				if (rle_j == 0 && j + 1 == (0xFF & cp.get(cp.position()))) {
					j = (0xFF & cp.get());
					rle_j = (0xFF & cp.get());
				} else if (rle_j != 0) {
					rle_j--;
					j++;
				} else {
					j = (0xFF & cp.get());
				}
			} while (j != 0);

			if (rle_i == 0 && i + 1 == (0xFF & cp.get(cp.position()))) {
				i = (0xFF & cp.get());
				rle_i = (0xFF & cp.get());
			} else if (rle_i != 0) {
				rle_i--;
				i++;
			} else {
				i = (0xFF & cp.get());
			}
		} while (i != 0);
	}

	static int[] calcFreqs_o0(ByteBuffer in) {
		int in_size = in.remaining();

		// Compute statistics
		int[] F = new int[256];
		int T = 0;
		for (int i = 0; i < in_size; i++) {
			F[0xFF & in.get()]++;
			T++;
		}
		long tr = ((long) Constants.TOTFREQ << 31) / T + (1 << 30) / T;

		// Normalise so T[i] == TOTFREQ
		int m = 0, M = 0;
		for (int j = 0; j < 256; j++) {
			if (m < F[j]) {
				m = F[j];
				M = j;
			}
		}

		int fsum = 0;
		for (int j = 0; j < 256; j++) {
			if (F[j] == 0)
				continue;
			if ((F[j] = (int) ((F[j] * tr) >> 31)) == 0)
				F[j] = 1;
			fsum += F[j];
		}

		fsum++;
		if (fsum < Constants.TOTFREQ)
			F[M] += Constants.TOTFREQ - fsum;
		else
			F[M] -= fsum - Constants.TOTFREQ;

		assert (F[M] > 0);
		return F;
	}

	static int[][] calcFreqs_o1(ByteBuffer in) {
		int in_size = in.remaining();

		int[][] F = new int[256][256];
		int[] T = new int[256];
		int c;

		int last_i = 0;
		for (int i = 0; i < in_size; i++) {
			F[last_i][c = (0xFF & in.get())]++;
			T[last_i]++;
			last_i = c;
		}
		F[0][0xFF & in.get(1 * (in_size >> 2))]++;
		F[0][0xFF & in.get(2 * (in_size >> 2))]++;
		F[0][0xFF & in.get(3 * (in_size >> 2))]++;
		T[0] += 3;

		for (int i = 0; i < 256; i++) {
			if (T[i] == 0)
				continue;

			double p = ((double) Constants.TOTFREQ) / T[i];
			int t2 = 0, m = 0, M = 0;
			for (int j = 0; j < 256; j++) {
				if (F[i][j] == 0)
					continue;

				if (m < F[i][j]) {
					m = F[i][j];
					M = j;
				}

				if ((F[i][j] *= p) == 0)
					F[i][j] = 1;
				t2 += F[i][j];
			}

			t2++;
			if (t2 < Constants.TOTFREQ)
				F[i][M] += Constants.TOTFREQ - t2;
			else
				F[i][M] -= t2 - Constants.TOTFREQ;
		}

		return F;
	}

	static RansEncSymbol[] buildSyms_o0(int[] F) {
		int C[] = new int[256];
		RansEncSymbol[] syms = new RansEncSymbol[256];
		for (int i = 0; i < syms.length; i++)
			syms[i] = new RansEncSymbol();

		int T = 0;
		for (int j = 0; j < 256; j++) {
			C[j] = T;
			T += F[j];
			if (F[j] != 0) {
				Encoding.RansEncSymbolInit(syms[j], C[j], F[j],
						Constants.TF_SHIFT);
			}
		}
		return syms;
	}

	static int writeFreqs_o0(ByteBuffer cp, int[] F) {
		int start = cp.position();

		int rle = 0;
		for (int j = 0; j < 256; j++) {
			if (F[j] != 0) {
				// j
				if (rle != 0) {
					rle--;
				} else {
					cp.put((byte) j);
					if (rle == 0 && j != 0 && F[j - 1] != 0) {
						for (rle = j + 1; rle < 256 && F[rle] != 0; rle++)
							;
						rle -= j + 1;
						cp.put((byte) rle);
					}
				}

				// F[j]
				if (F[j] < 128) {
					cp.put((byte) (F[j]));
				} else {
					cp.put((byte) (128 | (F[j] >> 8)));
					cp.put((byte) (F[j] & 0xff));
				}
			}
		}

		cp.put((byte) 0);
		return cp.position() - start;
	}

	static RansEncSymbol[][] buildSyms_o1(int F[][]) {
		RansEncSymbol[][] syms = new RansEncSymbol[256][256];
		for (int i = 0; i < syms.length; i++)
			for (int j = 0; j < syms[i].length; j++)
				syms[i][j] = new RansEncSymbol();

		for (int i = 0; i < 256; i++) {
			int[] F_i_ = F[i];
			int x = 0;
			for (int j = 0; j < 256; j++) {
				if (F_i_[j] != 0) {
					Encoding.RansEncSymbolInit(syms[i][j], x, F_i_[j],
							Constants.TF_SHIFT);
					x += F_i_[j];
				}
			}
		}

		return syms;
	}

	static int writeFreqs_o1(ByteBuffer cp, int F[][]) {
		int start = cp.position();
		int[] T = new int[256];

		for (int i = 0; i < 256; i++)
			for (int j = 0; j < 256; j++)
				T[i] += F[i][j];

		int rle_i = 0;
		for (int i = 0; i < 256; i++) {
			if (T[i] == 0)
				continue;

			// Store frequency table
			// i
			if (rle_i != 0) {
				rle_i--;
			} else {
				cp.put((byte) i);
				// FIXME: could use order-0 statistics to observe which alphabet
				// symbols are present and base RLE on that ordering instead.
				if (i != 0 && T[i - 1] != 0) {
					for (rle_i = i + 1; rle_i < 256 && T[rle_i] != 0; rle_i++)
						;
					rle_i -= i + 1;
					cp.put((byte) rle_i);
				}
			}

			int[] F_i_ = F[i];
			int rle_j = 0;
			for (int j = 0; j < 256; j++) {
				if (F_i_[j] != 0) {

					// j
					if (rle_j != 0) {
						rle_j--;
					} else {
						cp.put((byte) j);
						if (rle_j == 0 && j != 0 && F_i_[j - 1] != 0) {
							for (rle_j = j + 1; rle_j < 256 && F_i_[rle_j] != 0; rle_j++)
								;
							rle_j -= j + 1;
							cp.put((byte) rle_j);
						}
					}

					// F_i_[j]
					if (F_i_[j] < 128) {
						cp.put((byte) F_i_[j]);
					} else {
						cp.put((byte) (128 | (F_i_[j] >> 8)));
						cp.put((byte) (F_i_[j] & 0xff));
					}
				}
			}
			cp.put((byte) 0);
		}
		cp.put((byte) 0);

		return cp.position() - start;
	}

}
