/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.xstream;

import org.obiba.magma.test.AbstractMagmaTest;

import com.thoughtworks.xstream.XStream;

public abstract class AbstractMagmaXStreamTest extends AbstractMagmaTest {

  protected XStream getDefaultXStream() {
    return new DefaultXStreamFactory().createXStream();
  }
}
