package pallet.jclouds;

import com.google.common.base.Supplier;
import com.google.inject.TypeLiteral;

import org.jclouds.domain.Credentials;


// A class to provide access to TypeLiteral tokens that can
// not be accessed from clojure
public class Tokens {
  public static final TypeLiteral<Supplier<Credentials>> CREDENTIALS_SUPPLIER =
    new TypeLiteral<Supplier<Credentials>>(){};
}
