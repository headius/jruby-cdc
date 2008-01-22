module JRuby
  module OpenSSL
    GEM_ONLY = false unless defined?(GEM_ONLY)
  end
end

if JRuby::OpenSSL::GEM_ONLY
  require 'jruby/openssl/gem'
else
  module OpenSSL
    class OpenSSLError < StandardError; end

    # These require the gem
    %w[
    ASN1
    BN
    Cipher
    Config
    Netscape
    PKCS7
    PKey
    Random
    SSL
    X509
    ].each {|c| autoload c, "jruby/openssl/gem"}

    # These have fallbacks, but will still try to load the gem first
    %w[
    OPENSSL_VERSION
    OPENSSL_VERSION_NUMBER
    VERSION
    Digest
    DigestError
    HMAC
    HMACError
    ].each {|c| autoload c, "jruby/openssl/builtin"}
  end
end