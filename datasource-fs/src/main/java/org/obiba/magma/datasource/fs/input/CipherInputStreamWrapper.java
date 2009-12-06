package org.obiba.magma.datasource.fs.input;

import java.io.InputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;

import org.obiba.magma.datasource.fs.InputStreamWrapper;

import de.schlichtherle.io.File;

public class CipherInputStreamWrapper implements InputStreamWrapper {

  private Cipher cipher;

  public CipherInputStreamWrapper(Cipher cipher) {
    this.cipher = cipher;
  }

  @Override
  public InputStream wrap(InputStream is, File file) {
    return new CipherInputStream(is, cipher);
  }

}
