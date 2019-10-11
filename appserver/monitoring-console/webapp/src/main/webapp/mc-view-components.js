/*
   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
  
   Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
  
   The contents of this file are subject to the terms of either the GNU
   General Public License Version 2 only ("GPL") or the Common Development
   and Distribution License("CDDL") (collectively, the "License").  You
   may not use this file except in compliance with the License.  You can
   obtain a copy of the License at
   https://github.com/payara/Payara/blob/master/LICENSE.txt
   See the License for the specific
   language governing permissions and limitations under the License.
  
   When distributing the software, include this License Header Notice in each
   file and include the License file at glassfish/legal/LICENSE.txt.
  
   GPL Classpath Exception:
   The Payara Foundation designates this particular file as subject to the "Classpath"
   exception as provided by the Payara Foundation in the GPL Version 2 section of the License
   file that accompanied this code.
  
   Modifications:
   If applicable, add the following below the License Header, with the fields
   enclosed by brackets [] replaced by your own identifying information:
   "Portions Copyright [year] [name of copyright owner]"
  
   Contributor(s):
   If you wish your version of this file to be governed by only the CDDL or
   only the GPL Version 2, indicate your decision by adding "[Contributor]
   elects to include this software in this distribution under the [CDDL or GPL
   Version 2] license."  If you don't indicate a single choice of license, a
   recipient has the option to distribute your version of this file under
   either the CDDL, the GPL Version 2 or to extend the choice of license to
   its licensees as provided above.  However, if you add GPL Version 2 code
   and therefore, elected the GPL Version 2 license, then the option applies
   only if the new code is made subject to such option by the copyright
   holder.
*/

/*jshint esversion: 8 */

/**
 * Data/Model driven view components.
 *
 * Each of them gets passed a model which updates the view of the component to the model state.
 **/
MonitoringConsole.View.Components = (function() {

   const Units = MonitoringConsole.View.Units;
   const Selection = MonitoringConsole.Model.Page.Widgets.Selection;

   function element(fn) {
      let e = $.isFunction(fn) ? fn() : fn;
      return (typeof e === 'string') ? document.createTextNode(e) : e;
   }

   /**
    * This is the side panel showing the details and settings of widgets
    */
   let Settings = (function() {

      function emptyPanel() {
         return $('#panel-settings').empty();
      }

      function createHeaderRow(caption) {
         return $('<tr/>').append($('<th/>', {colspan: 2})
             .html(caption)
             .click(function() {
                 let tr = $(this).closest('tr').next();
                 let toggleAll = tr.children('th').length > 0;
                 while (tr.length > 0 && (toggleAll || tr.children('th').length == 0)) {
                     if (tr.children('th').length == 0) {
                         tr.children().toggle();                    
                     }
                     tr = tr.next();
                 }
         }));
      }

      function createCheckboxRow(label, checked, onChange) {
         return createRow(label, () => createCheckbox(checked, onChange));
      }

      function createTable(id, caption) {
         let table = $('<table />', { 'class': 'settings', id: id });
         if (caption)
            table.append(createHeaderRow(caption));
         return table;
      }

      function createRow(label, createInput) {
         return $('<tr/>').append($('<td/>').text(label)).append($('<td/>').append(element(createInput)));   
      }

      /**
      * Creates a checkbox to configure the attributes of a widget.
      *
      * @param {boolean} isChecked - if the created checkbox should be checked
      * @param {function} onChange - a function accepting two arguments: the updated widget and the checked state of the checkbox after a change
      */
      function createCheckbox(isChecked, onChange) {
         return $("<input/>", { type: 'checkbox', checked: isChecked })
             .on('change', function() {
                 let checked = this.checked;
                 Selection.configure((widget) => onChange(widget, checked));
             });
      }

      function createRangeRow(label, min, max, value, onChange) {
         let attributes = {type: 'number', value: value};
         if (min)
            attributes.min = min;
         if (max)
            attributes.max = max;
         return createRow(label, () => $('<input/>', attributes)
             .on('input change', function() {  
                 let val = this.valueAsNumber;
                 MonitoringConsole.View.onPageUpdate(Selection.configure((widget) => onChange(widget, val)));
             }));
      }

      function createDropdownRow(label, options, value, onChange) {
         let dropdown = $('<select/>');
         Object.keys(options).forEach(option => dropdown.append($('<option/>', {text:options[option], value:option, selected: value === option})));
         dropdown.change(() => MonitoringConsole.View.onPageUpdate(Selection.configure((widget) => onChange(widget, dropdown.val()))));
         return createRow(label, () => dropdown);
      }

      function createValueInputRow(label, value, unit, onChange) {
         if (unit === 'percent')
            return createRangeRow(label, 0, 100, value, onChange);
         if (unit === undefined || unit === 'count')
            createRangeRow(label, undefined, undefined, value, onChange);
         let converter = Units.converter(unit);
         let input = $('<input/>', {type: 'text', value: converter.format(value) });
         input.on('input change', function() {
            let val = converter.parse(this.value);
            MonitoringConsole.View.onPageUpdate(Selection.configure((widget) => onChange(widget, val)));
         });
         return createRow(label, input);
      }

      function onUpdate(model) {
         let panel = emptyPanel();
         for (let t = 0; t < model.length; t++) {
            let tableModel = model[t];
            let table = createTable(tableModel.id, tableModel.caption);
            panel.append(table);
            for (let r = 0; r < tableModel.entries.length; r++) {
               let rowModel = tableModel.entries[r];
               switch (rowModel.type) {
                  case 'header':
                     table.append(createHeaderRow(rowModel.label));
                     break;
                  case 'checkbox':
                     table.append(createCheckboxRow(rowModel.label, rowModel.value, rowModel.onChange));
                     break;
                  case 'dropdown':
                     table.append(createDropdownRow(rowModel.label, rowModel.options, rowModel.value, rowModel.onChange));
                     break;
                  case 'range':
                     table.append(createRangeRow(rowModel.label, rowModel.min, rowModel.max, rowModel.value, rowModel.onChange));
                     break;
                  case 'value':
                     table.append(createValueInputRow(rowModel.label, rowModel.value, rowModel.unit, rowModel.onChange));
                     break;
                  default:
                     if (rowModel.input) {
                        table.append(createRow(rowModel.label, rowModel.input));
                     } else {
                        table.append(createHeaderRow(rowModel.label));
                     }
               }
            }
         }
      }

      return { onUpdate: onUpdate };
    })();

    /**
     * Legend is a generic component showing a number of current values annotated with label and color.
     */ 
    let Legend = (function() {

      function createItem(label, value, color, assessments) {
         let strong = value;
         let normal = '';
         if (typeof value === 'string' && value.indexOf(' ') > 0) {
            strong = value.substring(0, value.indexOf(' '));
            normal = value.substring(value.indexOf(' '));
         }
         let style = {style: 'border-color: '+color+';'};
         if (assessments && assessments.level)
            style.class = 'level-'+assessments.level;
         return $('<li/>', style)
               .append($('<span/>').text(label))
               .append($('<strong/>').text(strong))
               .append($('<span/>').text(normal));
      }

      function onCreation(model) {
         let legend = $('<ol/>',  {'class': 'widget-legend-bar'});
         for (let i = 0; i < model.length; i++) {
            let itemModel = model[i];
            legend.append(createItem(itemModel.label, itemModel.value, itemModel.color, itemModel.assessments));
         }
         return legend;
      }

      return { onCreation: onCreation };
    })();

    /**
     * Component to navigate pages. More a less a dropdown.
     */
    let Navigation = (function() {

      function onUpdate(model) {
         let dropdown = $('<select/>', {id: 'page-nav-dropdown'});
         dropdown.change(() => model.onChange(dropdown.val()));
         for (let i = 0; i < model.pages.length; i++) {
            let pageModel = model.pages[i];
            dropdown.append($('<option/>', {value: pageModel.id, text: pageModel.label, selected: pageModel.active }));
            if (pageModel.active) {
               dropdown.val(pageModel.id);
            }
         }
         let nav = $("#panel-nav"); 
         nav.empty();
         nav.append(dropdown);
         return dropdown;
      }

      return { onUpdate: onUpdate };
    })();



    /*
     * Public API below:
     */
    return {
      /**
       * Call to update the settings side panel with the given model
       */
      onSettingsUpdate: (model) => Settings.onUpdate(model),
      /**
       * Call to update the top page navigation with the given model
       */
      onNavigationUpdate: (model) => Navigation.onUpdate(model),
      /**
       * Returns a jquery legend element reflecting the given model to be inserted into the DOM
       */
      onLegendCreation: (model) => Legend.onCreation(model),
      //TODO add id to model and make it an update?
    };

})();