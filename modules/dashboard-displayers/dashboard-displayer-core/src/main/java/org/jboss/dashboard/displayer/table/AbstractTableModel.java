/**
 * Copyright (C) 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.dashboard.displayer.table;

import org.jboss.dashboard.commons.comparator.ComparatorByCriteria;
import org.jboss.dashboard.commons.filter.FilterByCriteria;

public abstract class AbstractTableModel implements TableModel {

    protected FilterByCriteria filter;
    protected ComparatorByCriteria comparator;

    protected AbstractTableModel() {
        filter = null;
        comparator = null;
    }

    public void filter(FilterByCriteria filter) {
        this.filter = filter;
    }

    public void sort(ComparatorByCriteria comparator) {
        this.comparator = comparator;
    }

    public ComparatorByCriteria getComparator() {
        return comparator;
    }

    public int getColumnPosition(String columnName) {
        return -1;
    }

    public Object getValue(int row, String propertyName) {
        int column = getColumnPosition(propertyName);

        if (column < 0) return null;

        return getValueAt(row, column);
    }

    public String getColumnId(int index) {
        return getColumnName(index);
    }
}

