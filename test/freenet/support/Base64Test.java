package freenet.support;

import junit.framework.TestCase;
import java.util.Arrays;

public class Base64Test extends TestCase {
	
	/**
	 * Test the encode(byte[]) method
	 * against a well-known example
	 * (see http://en.wikipedia.org/wiki/Base_64 as reference)
	 * to verify if it encode works correctly.
	 */
	public void testEncode() {
		System.out.println("Base64-Encode");
		String toEncode = "Man is distinguished, not only by his reason, but by this singular passion from other animals, which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable generation of knowledge, exceeds the short vehemence of any carnal pleasure.";
		String expectedResult = "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlzIHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2YgdGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY29udGludWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRzIHRoZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4";
		byte[] aByteArrayToEncode = toEncode.getBytes();
		assertEquals(Base64.encode(aByteArrayToEncode),expectedResult);
	}
	
	/**
	 * Test the decode(String) method
	 * against a well-known example
	 * (see http://en.wikipedia.org/wiki/Base_64 as reference)
	 * to verify if it decode an already encoded string correctly.
	 */	
	public void testDecode() {
		System.out.println("Base64-Decode");
		String toDecode = "TWFuIGlzIGRpc3Rpbmd1aXNoZWQsIG5vdCBvbmx5IGJ5IGhpcyByZWFzb24sIGJ1dCBieSB0aGlzIHNpbmd1bGFyIHBhc3Npb24gZnJvbSBvdGhlciBhbmltYWxzLCB3aGljaCBpcyBhIGx1c3Qgb2YgdGhlIG1pbmQsIHRoYXQgYnkgYSBwZXJzZXZlcmFuY2Ugb2YgZGVsaWdodCBpbiB0aGUgY29udGludWVkIGFuZCBpbmRlZmF0aWdhYmxlIGdlbmVyYXRpb24gb2Yga25vd2xlZGdlLCBleGNlZWRzIHRoZSBzaG9ydCB2ZWhlbWVuY2Ugb2YgYW55IGNhcm5hbCBwbGVhc3VyZS4=";
		String expectedResult = "Man is distinguished, not only by his reason, but by this singular passion from other animals, which is a lust of the mind, that by a perseverance of delight in the continued and indefatigable generation of knowledge, exceeds the short vehemence of any carnal pleasure.";
		try {
			String decodedString = new String(Base64.decode(toDecode));
			assertEquals(decodedString,expectedResult);
		} catch (IllegalBase64Exception aException) {
			fail("Not expected exception thrown : " + aException.getMessage()); }
	}
	
	/**
	 * Test encode(byte[] in)
	 * and decode(String inStr) methods,
	 * to verify if they work correctly together.
	 * It compares the string before encoding
	 * and with the one after decoding.
	 */
	public void testEncodeDecode() {
		System.out.println("Base64-EncodeThenDecode");
		
		byte[] bytesDecoded;
		byte[] bytesToEncode = new byte[5];
		
		bytesToEncode[0] = 127;		//byte upper bound
		bytesToEncode[1] = 64;
		bytesToEncode[2] = 0;
		bytesToEncode[3] = -64;
		bytesToEncode[4] = -128;	//byte lower bound
		
		String aBase64EncodedString = Base64.encode(bytesToEncode);
		
		try {
			bytesDecoded = Base64.decode(aBase64EncodedString);
			assertTrue(Arrays.equals(bytesToEncode,bytesDecoded)); } 
		catch (IllegalBase64Exception aException) {
			fail("Not expected exception thrown : " + aException.getMessage()); }
	}
	
	/**
	 * Test the encode(String,boolean)
	 * method to verify if the padding
	 * character '=' is correctly placed.
	 */
	public void testEncodePadding() {
		System.out.println("Base64-encodePadding");
		byte[][] methodBytesArray = {
				{4,4,4},	//three byte Array -> no padding char expected	
				{4,4},		//two byte Array -> one padding char expected
				{4}};		//one byte Array -> two padding-chars expected	
		String encoded;
		
		for (int i = 0; i<methodBytesArray.length; i++) {
			encoded = Base64.encode(methodBytesArray[i],true);
			if (i == 0)
				assertEquals(encoded.indexOf('='),-1);	//no occurrences expected
			else
				assertEquals(encoded.indexOf('='),encoded.length()-i);
		}
	}
	
	/**
	 * Test if the decode(String) method
	 * raise correctly an exception when
	 * providing a string with non-Base64
	 * characters.
	 */
	public void testIllegalBaseCharacter() {
		System.out.println("Base64-illegalCharacter");
		String illegalCharString = "abcd=fghilmn";
		try {
			Base64.decode(illegalCharString);
			fail("Expected IllegalBase64Exception not thrown"); }
		catch (IllegalBase64Exception exception) {
			assertSame("illegal Base64 character",exception.getMessage()); }
	}
	
	/**
	 * Test if the decode(String) method
	 * raise correctly an exception when
	 * providing a string with a 
	 * wrong Base64 length.
	 * (as we can consider not-padded strings too,
	 *  the only wrong lengths are the ones
	 *  where -> number MOD 4 = 1).
	 */
	public void testIllegalBaseLength() {
		System.out.println("Base64-illegalLength");
		String illegalLengthString = "a";		//most interesting case
		try {
			Base64.decode(illegalLengthString);
			fail("Expected IllegalBase64Exception not thrown"); }
		catch (IllegalBase64Exception exception) {
			assertSame("illegal Base64 length",exception.getMessage()); }
	}
}
