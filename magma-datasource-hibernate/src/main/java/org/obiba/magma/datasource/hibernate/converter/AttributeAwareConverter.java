/*
 * Copyright (c) 2017 OBiBa. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.obiba.magma.datasource.hibernate.converter;

import java.util.Locale;

import org.obiba.magma.Attribute;
import org.obiba.magma.AttributeAware;
import org.obiba.magma.AttributeAwareBuilder;
import org.obiba.magma.Attributes;
import org.obiba.magma.datasource.hibernate.domain.AbstractAttributeAwareEntity;
import org.obiba.magma.datasource.hibernate.domain.AttributeState;

public class AttributeAwareConverter {

  public void addAttributes(AttributeAware attributeAware, AbstractAttributeAwareEntity hibernateEntity) {
    for(Attribute attr : attributeAware.getAttributes()) {
      AttributeState as;
      Locale locale = attr.isLocalised() ? attr.getLocale() : null;
      String namespace = attr.hasNamespace() ? attr.getNamespace() : null;
      if(hibernateEntity.hasAttribute(attr.getName(), namespace, locale)) {
        as = hibernateEntity.getAttribute(attr.getName(), namespace, locale);
      } else {
        as = new AttributeState(attr.getName(), namespace, locale, attr.getValue());
        hibernateEntity.addAttribute(as);
      }
      as.setValue(attr.getValue());
    }
  }

  public void setAttributes(AttributeAware attributeAware, AbstractAttributeAwareEntity hibernateEntity) {
    hibernateEntity.removeAllAttributes();
    addAttributes(attributeAware, hibernateEntity);
  }

  public void buildAttributeAware(AttributeAwareBuilder<?> builder, AbstractAttributeAwareEntity hibernateEntity) {
    for(AttributeState as : hibernateEntity.getAttributes()) {
      builder.addAttribute(Attributes.copyOf(as));
    }
  }

}
