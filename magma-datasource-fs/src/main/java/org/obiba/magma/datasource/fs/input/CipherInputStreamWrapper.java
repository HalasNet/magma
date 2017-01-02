/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.datasource.fs.input;

import java.io.InputStream;

import javax.crypto.CipherInputStream;

import org.obiba.magma.datasource.crypt.DatasourceCipherFactory;
import org.obiba.magma.datasource.fs.InputStreamWrapper;

import de.schlichtherle.io.File;

public class CipherInputStreamWrapper implements InputStreamWrapper {

  private final DatasourceCipherFactory cipherProvider;

  public CipherInputStreamWrapper(DatasourceCipherFactory cipherProvider) {
    this.cipherProvider = cipherProvider;
  }

  @Override
  public InputStream wrap(InputStream is, File file) {
    return new CipherInputStream(is, cipherProvider.createDecryptingCipher());
  }

}
