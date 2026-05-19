/*
    A WHIP client for raspberry pi
    Copyright (C) 2021  Tim Panton

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package pe.pi.RTLidar;

import com.ipseorama.slice.ORTC.RTCDtlsTransport;
import com.phono.srtplight.Log;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Properties;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import pe.pi.RTLidar.util.DTLSServer;

/**
 *
 * @author thp
 */
public abstract class DTLS {

    DTLSServer bcdtls;
    static BcTlsCrypto crypto = new BcTlsCrypto(CommandLine.random);
    private static AsymmetricKeyParameter key;
    private static TlsCertificate cert;
    RTCDtlsTransport dtlsT;

    public DTLS() {
    }

    public void stop() {
        bcdtls.stop();
    }

    public abstract void onReady(DTLSTransport trans);

    public void start(DatagramTransport dt, String ffp) {
        try {
            Log.debug("starting DTLS Server");
            bcdtls = new DTLSServer(key, cert, crypto, dt, ffp) {
                @Override
                public void onVerified(DTLSTransport trans) {
                    onReady(trans);
                }
            };

        } catch (Exception x) {
            Log.error("cant start DTLS because " + x.getMessage());
            x.printStackTrace();
        }

    }

    public static String getPrint(TlsCertificate fpc, boolean withColon) throws IOException {
        StringBuilder b = new StringBuilder();
        byte[] enc = fpc.getEncoded();
        SHA256Digest d = new SHA256Digest();
        d.update(enc, 0, enc.length);
        byte[] result = new byte[d.getDigestSize()];
        d.doFinal(result, 0);
        for (byte r : result) {
            String dig = Integer.toHexString((0xff) & r).toUpperCase();
            if (dig.length() == 1) {
                b.append('0');
            }
            b.append(dig);
            if (withColon) {
                b.append(":");
            }
        }
        if (withColon) {
            b.deleteCharAt(b.length() - 1);
        }
        return b.toString();
    }

    static protected int getLifespan() {
        return 1;//day
    }

    static protected String makeCn() {
        String ret = "RTCTransport-lidar";
        return ret;
    }

    static synchronized void mkCertNKey() throws InvalidKeyException, SignatureException, NoSuchAlgorithmException, NoSuchProviderException, CertificateEncodingException, IOException, KeyStoreException, CertificateException {
        if ((key == null) || (cert == null)) {
            Log.info("Making key and cert");

            int strength = 4096;
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(strength);

            KeyPair keyPair = generator.generateKeyPair();
            PrivateKey priv = keyPair.getPrivate();
            key = PrivateKeyFactory.createKey(priv.getEncoded());
            String dn = "CN=" + makeCn();
            X509Certificate tcert = generateCertificate(dn, keyPair,
                    getLifespan(), "SHA256withRSA");
            java.security.cert.Certificate[] chain = new java.security.cert.Certificate[1];

            ByteArrayInputStream bis = new ByteArrayInputStream(tcert.getEncoded());

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            while (bis.available() > 0) {
                chain[0] = cf.generateCertificate(bis);
            }
            cert = crypto.createCertificate(chain[0].getEncoded());
            Log.info("Made key and cert");
        } else {
            Log.info("reusing key and cert");

        }
    }

    static public X509Certificate generateCertificate(String dn, KeyPair pair,
            int days, String algorithm)
            throws CertificateException {

        try {
            AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find(algorithm);
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
            AsymmetricKeyParameter privateKeyAsymKeyParam = PrivateKeyFactory.createKey(pair.getPrivate().getEncoded());
            SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(pair.getPublic().getEncoded());
            ContentSigner sigGen = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(privateKeyAsymKeyParam);
            X500Name name = new X500Name(dn);
            Date from = new Date();
            Date to = new Date(from.getTime() + days * 86400000L);
            BigInteger sn = new BigInteger(64, new SecureRandom());

            X509v1CertificateBuilder v1CertGen = new X509v1CertificateBuilder(name, sn, from, to, name, subPubKeyInfo);
            X509CertificateHolder certificateHolder = v1CertGen.build(sigGen);
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);
        } catch (CertificateException ce) {
            throw ce;
        } catch (Exception e) {
            throw new CertificateException(e);
        }
    }

    String getPrint(boolean b) throws IOException {
        return getPrint(cert, b);
    }

    public static boolean validate(TlsCertificate fpc) throws IOException, CertificateException {
        boolean ret = false;

        ByteArrayInputStream bis = new ByteArrayInputStream(fpc.getEncoded());

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        while (bis.available() > 0) {
            java.security.cert.X509Certificate fiveohnine = (java.security.cert.X509Certificate) cf.generateCertificate(bis);
            long now = System.currentTimeMillis();
            Log.debug("cert " + fiveohnine.getSubjectDN()
                    + "\n\t not before " + fiveohnine.getNotBefore().getTime()
                    + "\n\t not after  " + fiveohnine.getNotAfter().getTime()
                    + "\n\t now it is  " + now);
            fiveohnine.checkValidity();
            ret = true;
        }
        return ret;
    }
}
