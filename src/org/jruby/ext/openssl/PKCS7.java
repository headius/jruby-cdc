/***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2006 Ola Bini <ola@ologix.com>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.ext.openssl;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.jruby.IRuby;
import org.jruby.RubyArray;
import org.jruby.RubyClass;
import org.jruby.RubyModule;
import org.jruby.RubyObject;
import org.jruby.ext.openssl.x509store.PEM;
import org.jruby.ext.openssl.x509store.X509AuxCertificate;
import org.jruby.ext.openssl.x509store.X509_STORE_CTX;
import org.jruby.runtime.Block;
import org.jruby.runtime.CallbackFactory;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.builtin.IRubyObject;

/**
 * @author <a href="mailto:ola.bini@ki.se">Ola Bini</a>
 */
public class PKCS7 extends RubyObject {    
    public static void createPKCS7(IRuby runtime, RubyModule mOSSL) {
        RubyModule mPKCS7 = mOSSL.defineModuleUnder("PKCS7");
        RubyClass openSSLError = runtime.getModule("OpenSSL").getClass("OpenSSLError");
        mPKCS7.defineClassUnder("PKCS7Error",openSSLError,openSSLError.getAllocator());
        RubyClass cPKCS7 = mPKCS7.defineClassUnder("PKCS7",runtime.getObject(),runtime.getObject().getAllocator());

        cPKCS7.attr_accessor(new IRubyObject[]{runtime.newSymbol("data"),runtime.newSymbol("error_string")});

        CallbackFactory p7cb = runtime.callbackFactory(PKCS7.class);
        mPKCS7.getMetaClass().defineFastMethod("read_smime",p7cb.getFastSingletonMethod("read_smime",IRubyObject.class));
        mPKCS7.getMetaClass().defineFastMethod("write_smime",p7cb.getFastOptSingletonMethod("write_smime"));
        mPKCS7.getMetaClass().defineFastMethod("sign",p7cb.getFastOptSingletonMethod("sign"));
        mPKCS7.getMetaClass().defineFastMethod("encrypt",p7cb.getFastOptSingletonMethod("encrypt"));
        // FIXME: This was defined, but does not exist?  Should it?
        //cPKCS7.getMetaClass().defineFastMethod("new",p7cb.getFastOptSingletonMethod("newInstance"));
        cPKCS7.defineMethod("initialize",p7cb.getOptMethod("_initialize"));
        cPKCS7.defineFastMethod("initialize_copy",p7cb.getFastMethod("initialize_copy",IRubyObject.class));
        cPKCS7.defineFastMethod("clone",p7cb.getFastMethod("rbClone"));
        cPKCS7.defineFastMethod("type=",p7cb.getFastMethod("set_type",IRubyObject.class));
        cPKCS7.defineFastMethod("type",p7cb.getFastMethod("get_type"));
        cPKCS7.defineFastMethod("detached=",p7cb.getFastMethod("set_detached",IRubyObject.class));
        cPKCS7.defineFastMethod("detached",p7cb.getFastMethod("detached"));
        cPKCS7.defineFastMethod("detached?",p7cb.getFastMethod("detached_p"));
        cPKCS7.defineFastMethod("cipher=",p7cb.getFastMethod("set_cipher",IRubyObject.class));
        cPKCS7.defineFastMethod("add_signer",p7cb.getFastMethod("add_signer",IRubyObject.class));
        cPKCS7.defineFastMethod("signers",p7cb.getFastMethod("signers"));
        cPKCS7.defineFastMethod("add_recipient",p7cb.getFastMethod("add_recipient",IRubyObject.class));
        cPKCS7.defineFastMethod("recipients",p7cb.getFastMethod("recipients"));
        cPKCS7.defineFastMethod("add_certificate",p7cb.getFastMethod("add_certificate",IRubyObject.class));
        cPKCS7.defineFastMethod("certificates=",p7cb.getFastMethod("set_certificates",IRubyObject.class));
        cPKCS7.defineFastMethod("certificates",p7cb.getFastMethod("certificates"));
        cPKCS7.defineFastMethod("add_crl",p7cb.getFastMethod("add_crl",IRubyObject.class));
        cPKCS7.defineFastMethod("crls=",p7cb.getFastMethod("set_crls",IRubyObject.class));
        cPKCS7.defineFastMethod("crls",p7cb.getFastMethod("crls"));
        cPKCS7.defineFastMethod("add_data",p7cb.getFastMethod("add_data",IRubyObject.class));
        cPKCS7.defineFastMethod("data=",p7cb.getFastMethod("add_data",IRubyObject.class));
        cPKCS7.defineFastMethod("verify",p7cb.getFastOptMethod("verify"));
        cPKCS7.defineFastMethod("decrypt",p7cb.getFastOptMethod("decrypt"));
        cPKCS7.defineFastMethod("to_pem",p7cb.getFastMethod("to_pem"));
        cPKCS7.defineFastMethod("to_s",p7cb.getFastMethod("to_pem"));
        cPKCS7.defineFastMethod("to_der",p7cb.getFastMethod("to_der"));

        SignerInfo.createSignerInfo(runtime,mPKCS7);
        RecipientInfo.createRecipientInfo(runtime,mPKCS7);

        mPKCS7.setConstant("TEXT",runtime.newFixnum(1));
        mPKCS7.setConstant("NOCERTS",runtime.newFixnum(2));
        mPKCS7.setConstant("NOSIGS",runtime.newFixnum(4));
        mPKCS7.setConstant("NOCHAIN",runtime.newFixnum(8));
        mPKCS7.setConstant("NOINTERN",runtime.newFixnum(16));
        mPKCS7.setConstant("NOVERIFY",runtime.newFixnum(32));
        mPKCS7.setConstant("DETACHED",runtime.newFixnum(64));
        mPKCS7.setConstant("BINARY",runtime.newFixnum(128));
        mPKCS7.setConstant("NOATTR",runtime.newFixnum(256));
        mPKCS7.setConstant("NOSMIMECAP",runtime.newFixnum(512));
    }

    public static IRubyObject read_smime(IRubyObject recv, IRubyObject arg) {
        System.err.println("WARNING: un-implemented method called PKCS7#read_smime");
        return recv.getRuntime().getNil();
    }

    public static IRubyObject write_smime(IRubyObject recv, IRubyObject[] args) {
        System.err.println("WARNING: un-implemented method called PKCS7#write_smime");
        return recv.getRuntime().getNil();
    }

    public static IRubyObject sign(IRubyObject recv, IRubyObject[] args) throws Exception {
        IRubyObject cert = recv.getRuntime().getNil();
        IRubyObject key = recv.getRuntime().getNil();
        IRubyObject data = recv.getRuntime().getNil();
        IRubyObject certs = recv.getRuntime().getNil();
        //IRubyObject flags = recv.getRuntime().getNil();
        recv.checkArgumentCount(args,3,5);
        switch(args.length) {
        case 5:
            //flags = args[4];
        case 4:
            certs = args[3];
        case 3:
            cert = args[0];
            key = args[1];
            data = args[2];
        }

        X509AuxCertificate x509 = ((X509Cert)cert).getAuxCert();
        PrivateKey pkey = ((PKey)key).getPrivateKey();
        String in = data.toString();
        List x509s = null;
        if(!certs.isNil()) {
            x509s = new ArrayList();
            for(Iterator iter = ((RubyArray)certs).getList().iterator();iter.hasNext();) {
                x509s.add(((X509Cert)iter.next()).getAuxCert());
            }
            x509s.add(x509);
        }

        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();

        gen.addSigner(pkey,x509,"1.3.14.3.2.26"); //SHA1 OID
        if(x509s != null) {
            CertStore store = CertStore.getInstance("Collection", new CollectionCertStoreParameters(x509s));
            gen.addCertificatesAndCRLs(store);
        }
        CMSSignedData sdata = gen.generate(new CMSProcessableByteArray(in.getBytes("PLAIN")),"BC");
        
        PKCS7 ret = new PKCS7(recv.getRuntime(),((RubyClass)((RubyModule)(recv.getRuntime().getModule("OpenSSL").getConstant("PKCS7"))).getConstant("PKCS7")));
        ret.setInstanceVariable("@data",recv.getRuntime().getNil());
        ret.setInstanceVariable("@error_string",recv.getRuntime().getNil());
        ret.signedData = sdata;

        return ret;
    }

    public static IRubyObject encrypt(IRubyObject recv, IRubyObject[] args) {
        System.err.println("WARNING: un-implemented method called PKCS7#encrypt");
        return recv.getRuntime().getNil();
    }

    public PKCS7(IRuby runtime, RubyClass type) {
        super(runtime,type);
    }

    private CMSSignedData signedData;

    public IRubyObject _initialize(IRubyObject[] args, Block unusedBlock) throws Exception {
        if(checkArgumentCount(args,0,1) == 0) {
            return this;
        }
        IRubyObject arg = OpenSSLImpl.to_der_if_possible(args[0]);
        byte[] b = arg.toString().getBytes("PLAIN");
        signedData = PEM.read_PKCS7(new InputStreamReader(new ByteArrayInputStream(b)),null);
        if(null == signedData) {
            signedData = new CMSSignedData(ContentInfo.getInstance(new ASN1InputStream(b).readObject()));
        }
        this.setInstanceVariable("@data",getRuntime().getNil());
        this.setInstanceVariable("@error_string",getRuntime().getNil());
        return this;
    }

    public IRubyObject initialize_copy(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#init_copy");
        if(this == obj) {
            return this;
        }
        checkFrozen();
        return this;
    }

    public IRubyObject set_type(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#type=");
        return getRuntime().getNil();
    }

    public IRubyObject get_type() {
        System.err.println("WARNING: un.implemented method called PKCS7#type");
        return getRuntime().getNil();
    }

    public IRubyObject set_detached(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#detached=");
        return getRuntime().getNil();
    }

    public IRubyObject detached() {
        System.err.println("WARNING: un.implemented method called PKCS7#detached");
        return getRuntime().getNil();
    }

    public IRubyObject detached_p() {
        System.err.println("WARNING: un.implemented method called PKCS7#detached?");
        return getRuntime().getNil();
    }

    public IRubyObject set_cipher(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#cipher=");
        return getRuntime().getNil();
    }

    public IRubyObject add_signer(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#add_signer");
        return getRuntime().getNil();
    }

    public IRubyObject signers() {
        System.err.println("WARNING: un.implemented method called PKCS7#signers");
        return getRuntime().getNil();
    }

    public IRubyObject add_recipient(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#add_recipient");
        return getRuntime().getNil();
    }

    public IRubyObject recipients() {
        System.err.println("WARNING: un.implemented method called PKCS7#recipients");
        return getRuntime().getNil();
    }

    public IRubyObject add_certificate(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#add_certificate");
        return getRuntime().getNil();
    }

    public IRubyObject set_certificates(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#certificates=");
        return getRuntime().getNil();
    }

    public IRubyObject certificates() throws Exception {
        CertStore cc = signedData.getCertificatesAndCRLs("Collection","BC");
        List l = X509_STORE_CTX.transform(cc.getCertificates(null));
        return getRuntime().newArray(l);
    }

    public IRubyObject add_crl(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#add_crl");
        return getRuntime().getNil();
    }

    public IRubyObject set_crls(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#crls=");
        return getRuntime().getNil();
    }

    public IRubyObject crls() {
        System.err.println("WARNING: un.implemented method called PKCS7#crls");
        return getRuntime().getNil();
    }

    public IRubyObject add_data(IRubyObject obj) {
        System.err.println("WARNING: un.implemented method called PKCS7#add_data");
        return getRuntime().getNil();
    }

    public IRubyObject verify(IRubyObject[] args) throws Exception {
        IRubyObject certs;
        //IRubyObject store;
        IRubyObject indata = getRuntime().getNil();
        //IRubyObject flags = getRuntime().getNil();
        switch(checkArgumentCount(args,2,4)) {
        case 4:
            //flags = args[3];
        case 3:
            indata = args[2];
        default:
            certs = args[0];
            //store = args[1];
        }
        
        if(indata.isNil()) {
            indata = getInstanceVariable("@data");
        }
        List x509s = null;
        if(!certs.isNil()) {
            x509s = new ArrayList();
            for(Iterator iter = ((RubyArray)certs).getList().iterator();iter.hasNext();) {
                x509s.add(((X509Cert)iter.next()).getAuxCert());
            }
        }

        CertStore _x509s = CertStore.getInstance("Collection", new CollectionCertStoreParameters(x509s));

        int verified = 0;

        SignerInformationStore  signers =  signedData.getSignerInfos();
        CertStore  cs =                    signedData.getCertificatesAndCRLs("Collection","BC");
        Collection              c = signers.getSigners();
        Iterator                it = c.iterator();
  
        while(it.hasNext()) {
            SignerInformation   signer = (SignerInformation)it.next();
            System.err.println(signer.getSignedAttributes().toHashtable());

            Collection          certCollection = _x509s.getCertificates(signer.getSID());
            Iterator        certIt = certCollection.iterator();
            X509Certificate cert = null;

            if(certIt.hasNext()) {
                cert = (X509AuxCertificate)certIt.next();
            }
            if(cert == null) {
                Collection          certCollection2 = cs.getCertificates(signer.getSID());
                Iterator        certIt2 = certCollection2.iterator();
                if(certIt2.hasNext()) {
                    cert = (X509Certificate)certIt2.next();
                }                
            }
            if(null != cert && signer.verify(cert,"BC")) {
                verified++;
            }   
        }

        return (verified != 0) ? getRuntime().getTrue() : getRuntime().getFalse();
    }

    public IRubyObject decrypt(IRubyObject[] args) {
        System.err.println("WARNING: un.implemented method called PKCS7#decrypt");
        return getRuntime().getNil();
    }

    public IRubyObject to_pem() throws Exception {
        StringWriter w = new StringWriter();
        PEM.write_PKCS7(w,signedData);
        w.close();
        return getRuntime().newString(w.toString());
    }

    public IRubyObject to_der() throws Exception {
        return getRuntime().newString(new String(signedData.getEncoded(),"ISO8859_1"));
    }

    public IRubyObject rbClone() {
        IRubyObject clone = new PKCS7(getRuntime(),getMetaClass().getRealClass());
        clone.setMetaClass(getMetaClass().getSingletonClassClone());
        clone.setTaint(this.isTaint());
        clone.initCopy(this);
        clone.setFrozen(isFrozen());
        return clone;
    }

    public static class SignerInfo extends RubyObject {
        private static ObjectAllocator SIGNERINFO_ALLOCATOR = new ObjectAllocator() {
            public IRubyObject allocate(IRuby runtime, RubyClass klass) {
                return new SignerInfo(runtime, klass);
            }
        };
    
        public static void createSignerInfo(IRuby runtime, RubyModule mPKCS7) {
            RubyClass cPKCS7Signer = mPKCS7.defineClassUnder("SignerInfo",runtime.getObject(),SIGNERINFO_ALLOCATOR);
            mPKCS7.defineConstant("Signer",cPKCS7Signer);

            CallbackFactory p7scb = runtime.callbackFactory(SignerInfo.class);
            cPKCS7Signer.defineFastMethod("initialize",p7scb.getFastMethod("initialize",IRubyObject.class,IRubyObject.class,IRubyObject.class));
            cPKCS7Signer.defineFastMethod("issuer",p7scb.getFastMethod("issuer"));
            cPKCS7Signer.defineFastMethod("name",p7scb.getFastMethod("issuer"));
            cPKCS7Signer.defineFastMethod("serial",p7scb.getFastMethod("serial"));
            cPKCS7Signer.defineFastMethod("signed_time",p7scb.getFastMethod("signed_time"));
        }

        public SignerInfo(IRuby runtime, RubyClass type) {
            super(runtime,type);
        }

        public IRubyObject initialize(IRubyObject arg1, IRubyObject arg2, IRubyObject arg3) {
            System.err.println("WARNING: un-implemented method called SignerInfo#initialize");
            return this;
        }

        public IRubyObject issuer() {
            System.err.println("WARNING: un-implemented method called SignerInfo#issuer");
            return getRuntime().getNil();
        }

        public IRubyObject serial() {
            System.err.println("WARNING: un-implemented method called SignerInfo#serial");
            return getRuntime().getNil();
        }

        public IRubyObject signed_time() {
            System.err.println("WARNING: un-implemented method called SignerInfo#signed_time");
            return getRuntime().getNil();
        }
    }

    public static class RecipientInfo extends RubyObject {
        private static ObjectAllocator RECIPIENTINFO_ALLOCATOR = new ObjectAllocator() {
            public IRubyObject allocate(IRuby runtime, RubyClass klass) {
                return new RecipientInfo(runtime, klass);
            }
        };
    
        public static void createRecipientInfo(IRuby runtime, RubyModule mPKCS7) {
            RubyClass cPKCS7Recipient = mPKCS7.defineClassUnder("RecipientInfo",runtime.getObject(),RECIPIENTINFO_ALLOCATOR);

            CallbackFactory p7rcb = runtime.callbackFactory(RecipientInfo.class);
            cPKCS7Recipient.defineFastMethod("initialize",p7rcb.getFastMethod("initialize",IRubyObject.class));
            cPKCS7Recipient.defineFastMethod("issuer",p7rcb.getFastMethod("issuer"));
            cPKCS7Recipient.defineFastMethod("serial",p7rcb.getFastMethod("serial"));
            cPKCS7Recipient.defineFastMethod("enc_key",p7rcb.getFastMethod("enc_key"));
        }

        public RecipientInfo(IRuby runtime, RubyClass type) {
            super(runtime,type);
        }

        public IRubyObject initialize(IRubyObject arg) {
            System.err.println("WARNING: un-implemented method called RecipientInfo#initialize");
            return this;
        }

        public IRubyObject issuer() {
            System.err.println("WARNING: un-implemented method called RecipientInfo#issuer");
            return getRuntime().getNil();
        }

        public IRubyObject serial() {
            System.err.println("WARNING: un-implemented method called RecipientInfo#serial");
            return getRuntime().getNil();
        }

        public IRubyObject enc_key() {
            System.err.println("WARNING: un-implemented method called RecipientInfo#enc_key");
            return getRuntime().getNil();
        }
    }
}// PKCS7
