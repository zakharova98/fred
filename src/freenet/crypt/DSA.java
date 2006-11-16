/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.crypt;

import java.math.BigInteger;
import java.util.Random;
import java.security.SecureRandom;

import freenet.support.Logger;

import net.i2p.util.NativeBigInteger;

/**
 * Implements the Digital Signature Algorithm (DSA) described in FIPS-186
 */
public class DSA {

	/**
	 * Returns a DSA signature given a group, private key (x), a random nonce
	 * (k), and the hash of the message (m).
	 */
	public static DSASignature sign(DSAGroup g,
			DSAPrivateKey x,
			BigInteger k, 
			BigInteger m,
			RandomSource random) {
		BigInteger r=g.getG().modPow(k, g.getP()).mod(g.getQ());

		BigInteger kInv=k.modInverse(g.getQ());
		return sign(g, x, r, kInv, m, random);
	} 

	public static DSASignature sign(DSAGroup g, DSAPrivateKey x, BigInteger m,
			RandomSource r) {
		BigInteger k = DSA.generateK(g, r);
		return sign(g, x, k, m, r);
	}

	/**
	 * Precalculates a number of r, kInv pairs given a random source
	 */
	public static BigInteger[][] signaturePrecalculate(DSAGroup g,
			int count, Random r) {
		BigInteger[][] result=new BigInteger[count][2];

		for (int i=0; i<count; i++) {
			BigInteger k = DSA.generateK(g, r);

			result[i][0] = g.getG().modPow(k, g.getP()); // r 
			result[i][1] = k.modInverse(g.getQ()); // k^-1 
		}
		return result;
	}

	/**
	 * Returns a DSA signature given a group, private key (x), 
	 * the precalculated values of r and k^-1, and the hash
	 * of the message (m)
	 */
	public static DSASignature sign(DSAGroup g, DSAPrivateKey x,
			BigInteger r, BigInteger kInv, 
			BigInteger m, RandomSource random) {
		BigInteger s1=m.add(x.getX().multiply(r)).mod(g.getQ());
		BigInteger s=kInv.multiply(s1).mod(g.getQ());
		if((r.compareTo(BigInteger.ZERO) == 0) || (s.compareTo(BigInteger.ZERO) == 0)) {
			Logger.normal(DSA.class, "R or S equals 0 : Weird behaviour detected, please report if seen too often.");
			return sign(g, x, r, generateK(g, random), m, random);
		}
		return new DSASignature(r,s);
	}

	private static BigInteger generateK(DSAGroup g, Random r){
		BigInteger k;
		do {
			k=new NativeBigInteger(DSAGroup.Q_BIT_LENGTH, r);
		} while ((g.getQ().compareTo(k) < 1) || (k.compareTo(BigInteger.ZERO) == 0));
		return k;
	}

	/**
	 * Verifies the message authenticity given a group, the public key
	 * (y), a signature, and the hash of the message (m).
	 */
	public static boolean verify(DSAPublicKey kp,
			DSASignature sig,
			BigInteger m) {
		try {
			// 0<r<q has to be true
			if((sig.getR().compareTo(BigInteger.ZERO) < 1) || (kp.getQ().compareTo(sig.getR()) < 1)) return false;
			// 0<s<q has to be true as well
			if((sig.getS().compareTo(BigInteger.ZERO) < 1) || (kp.getQ().compareTo(sig.getS()) < 1)) return false;

			BigInteger w=sig.getS().modInverse(kp.getQ());
			BigInteger u1=m.multiply(w).mod(kp.getQ());
			BigInteger u2=sig.getR().multiply(w).mod(kp.getQ());
			BigInteger v1=kp.getG().modPow(u1, kp.getP());
			BigInteger v2=kp.getY().modPow(u2, kp.getP());
			BigInteger v=v1.multiply(v2).mod(kp.getP()).mod(kp.getQ());
			return v.equals(sig.getR());

			//FIXME: is there a better way to handle this exception raised on the 'w=' line above?
		} catch (ArithmeticException e) {  // catch error raised by invalid data
			return false;                  // and report that that data is bad.
		}
	}

	public static void main(String[] args) throws Exception {
		//DSAGroup g=DSAGroup.readFromField(args[0]);
		DSAGroup g = Global.DSAgroupBigA;
		//Yarrow y=new Yarrow();
		DummyRandomSource y = new DummyRandomSource();
		DSAPrivateKey pk=new DSAPrivateKey(g, y);
		DSAPublicKey pub=new DSAPublicKey(g, pk);
		DSASignature sig=sign(g, pk, BigInteger.ZERO, y);
		System.err.println(verify(pub, sig, BigInteger.ZERO));
		while(true) {
			long totalTimeSigning = 0;
			long totalTimeVerifying = 0;
			long totalRSize = 0;
			long totalSSize = 0;
			long totalPubKeySize = 0;
			long totalPrivKeySize = 0;
			int maxPrivKeySize = 0;
			int maxPubKeySize = 0;
			int maxRSize = 0;
			int maxSSize = 0;
			int totalRUnsignedBitSize = 0;
			int maxRUnsignedBitSize = 0;
			Random r = new Random(y.nextLong());
			byte[] msg = new byte[32];
			for(int i=0;i<1000;i++) {
				r.nextBytes(msg);
				BigInteger m = new BigInteger(1, msg);
				pk = new DSAPrivateKey(g, r);
				int privKeySize = pk.asBytes().length;
				totalPrivKeySize += privKeySize;
				if(privKeySize > maxPrivKeySize) maxPrivKeySize = privKeySize;
				pub = new DSAPublicKey(g, pk);
				int pubKeySize = pub.asBytes().length;
				totalPubKeySize += pubKeySize;
				if(pubKeySize > maxPubKeySize) maxPubKeySize = pubKeySize;
				long t1 = System.currentTimeMillis();
				sig = sign(g, pk, m, y);
				long t2 = System.currentTimeMillis();
				if(!verify(pub, sig, m)) {
					System.err.println("Failed to verify!");
				}
				long t3 = System.currentTimeMillis();
				totalTimeSigning += (t2 - t1);
				totalTimeVerifying += (t3 - t2);
				int rSize = sig.getR().bitLength();
				rSize = (rSize / 8) + (rSize % 8 == 0 ? 0 : 1);
				totalRSize += rSize;
				if(rSize > maxRSize) maxRSize = rSize;
				int rUnsignedBitSize = sig.getR().bitLength();
				totalRUnsignedBitSize += rUnsignedBitSize;
				maxRUnsignedBitSize = Math.max(maxRUnsignedBitSize, rUnsignedBitSize);
				int sSize = sig.getS().bitLength();
				sSize = sSize / 8 +  (sSize % 8 == 0 ? 0 : 1);
				totalSSize += sSize;
				if(sSize > maxSSize) maxSSize = sSize;
			}
			System.out.println("Total time signing: "+totalTimeSigning);
			System.out.println("Total time verifying: "+totalTimeVerifying);
			System.out.println("Total R size: "+totalRSize+" (max "+maxRSize+ ')');
			System.out.println("Total S size: "+totalSSize+" (max "+maxSSize+ ')');
			System.out.println("Total R unsigned bitsize: "+totalRUnsignedBitSize);
			System.out.println("Total pub key size: "+totalPubKeySize+" (max "+maxPubKeySize+ ')');
			System.out.println("Total priv key size: "+totalPrivKeySize+" (max "+maxPrivKeySize+ ')');
		}
    }
}
