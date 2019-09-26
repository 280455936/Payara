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
 *
 **/
MonitoringConsole.View = (function() {

    /**
     * Updates the DOM with the page navigation tabs so it reflects current model state
     */ 
    function updatePageNavigation() {
        let nav = $("#pagesTabs"); 
        nav.empty();
        MonitoringConsole.Model.listPages().forEach(function(page) {
            let tabId = page.id + '-tab';
            let css = "page-tab" + (page.active ? ' page-selected' : '');
            let pageTab = $('<span/>', {id: tabId, "class": css, text: page.name});
            if (page.active) {
                pageTab.click(function() {
                    MonitoringConsole.Model.Settings.toggle();
                    updatePageAndSelectionSettings();
                });
            } else {
                pageTab.click(() => onPageChange(MonitoringConsole.Model.Page.changeTo(page.id)));                
            }
            nav.append(pageTab);
        });
        let addPage = $('<span/>', {id: 'addPageTab', 'class': 'page-tab'}).html('&plus;');
        addPage.click(() => onPageChange(MonitoringConsole.Model.Page.create('(Unnamed)')));
        nav.append(addPage);
    }

    /**
     * Updates the DOM with the page and selection settings so it reflects current model state
     */ 
    function updatePageAndSelectionSettings() {
        let panelConsole = $('#console');
        if (MonitoringConsole.Model.Settings.isDispayed()) {
            if (!panelConsole.hasClass('state-show-settings')) {
                panelConsole.addClass('state-show-settings');                
            }
            let panelSettings = $('#panel-settings');
            panelSettings
                .empty()
                .append($('<button/>', { title: 'Delete current page', 'class': 'btnIcon btnClose' }).html('&times;').click(MonitoringConsole.View.onPageDelete))
                .append(createPageSettings())
                .append(createDataSettings());
            if (MonitoringConsole.Model.Page.Widgets.Selection.isSingle()) {
                panelSettings.append(createWidgetSettings(MonitoringConsole.Model.Page.Widgets.Selection.first()));
            }
        } else {
            panelConsole.removeClass('state-show-settings');
        }
    }

    /**
     * Each chart needs to be in a relative positioned box to allow responsive sizing.
     * This fuction creates this box including the canvas element the chart is drawn upon.
     */
    function createWidgetTargetContainer(cell) {
        let boxId = cell.widget.target + '-box';
        let box = $('#'+boxId);
        if (box.length > 0)
            return box.first();
        box = $('<div/>', { id: boxId, "class": "chart-box" });
        box.append($('<canvas/>',{ id: cell.widget.target }));
        return box;
    }

    function toWords(str) {
        // camel case to words
        let res = str.replace(/([A-Z]+)/g, " $1").replace(/([A-Z][a-z])/g, " $1");
        if (res.indexOf('.') > 0) {
            // dots to words with upper casing each word
            return res.replace(/\.([a-z])/g, " $1").split(' ').map((s) => s.charAt(0).toUpperCase() + s.substring(1)).join(' ');
        }
        return res;
    }

    function formatSeriesName(widget) {
        let series = widget.series;
        let endOfTags = series.lastIndexOf(' ');
        let metric = endOfTags <= 0 ? series : series.substring(endOfTags + 1);
        if (endOfTags <= 0 )
            return toWords(metric);
        let tags = series.substring(0, endOfTags).split(' ');
        let text = '';
        for (let i = 0; i < tags.length; i++) {
            let tag = tags[i];
            if (tag.startsWith('@:')) {
                text += '<code>'+tag.substring(2)+'</code> ';
            } else {
                text +='<i>'+tag+'</i> ';
            }
        }
        text += toWords(metric);
        if (widget.options.perSec) {
            text += ' <i>(1/sec)</i>';
        }
        return text;
    }

    function createWidgetToolbar(cell) {
        let series = cell.widget.series;
        return $('<div/>', {"class": "caption-bar"})
            .append($('<h3/>', {title: 'Select '+series}).html(formatSeriesName(cell.widget))
                .click(() => onWidgetToolbarClick(cell.widget)))
            .append(createToolbarButton('Remove chart from page', '&times;', () => onWidgetDelete(series)))
            .append(createToolbarButton('Enlarge this chart', '&plus;', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.spanMore(series))))
            .append(createToolbarButton('Shrink this chart', '&minus;', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.spanLess(series))))
            .append(createToolbarButton('Move to right', '&#9655;', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.moveRight(series))))
            .append(createToolbarButton('Move to left', '&#9665;', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.moveLeft(series))));
    }

    function createToolbarButton(title, icon, onClick) {
        return $('<button/>', { "class": "btnIcon", title: title}).html(icon).click(onClick);
    }


    function createWidgetSettings(widget) {
        let settings = createSettingsTable('settings-widget')
            .append(createSettingsHeaderRow(formatSeriesName(widget)))
            .append(createSettingsHeaderRow('General'))
            .append(createSettingsDropdownRow('Type', {line: 'Time Curve', bar: 'Range Indicator'}, widget.type, (widget, selected) => widget.type = selected))
            .append(createSettingsSliderRow('Span', 1, 4, widget.grid.span || 1, (widget, value) => widget.grid.span = value))
            .append(createSettingsSliderRow('Column', 1, 4, 1 + (widget.grid.column || 0), (widget, value) => widget.grid.column = value - 1))
            .append(createSettingsSliderRow('Item', 1, 4, 1 + (widget.grid.item || 0), (widget, value) => widget.grid.item = value - 1))
            .append(createSettingsHeaderRow('Data'))
            .append(createSettingsCheckboxRow('Add Minimum', widget.options.drawMinLine, (widget, checked) => widget.options.drawMinLine = checked))
            .append(createSettingsCheckboxRow('Add Maximum', widget.options.drawMaxLine, (widget, checked) => widget.options.drawMaxLine = checked))            
            ;
        if (widget.type === 'line') {
            settings
            .append(createSettingsCheckboxRow('Add Average', widget.options.drawAvgLine, (widget, checked) => widget.options.drawAvgLine = checked))
            .append(createSettingsCheckboxRow('Per Second', widget.options.perSec, (widget, checked) => widget.options.perSec = checked))
            .append(createSettingsHeaderRow('Display Options'))
            .append(createSettingsCheckboxRow('Begin at Zero', widget.options.beginAtZero, (widget, checked) => widget.options.beginAtZero = checked))
            .append(createSettingsCheckboxRow('Automatic Labels', widget.options.autoTimeTicks, (widget, checked) => widget.options.autoTimeTicks = checked))
            .append(createSettingsCheckboxRow('Use Bezier Curves', widget.options.drawCurves, (widget, checked) => widget.options.drawCurves = checked))
            .append(createSettingsCheckboxRow('Use Animations', widget.options.drawAnimations, (widget, checked) => widget.options.drawAnimations = checked))
            .append(createSettingsCheckboxRow('Label X-Axis at 90°', widget.options.rotateTimeLabels, (widget, checked) => widget.options.rotateTimeLabels = checked))
            .append(createSettingsCheckboxRow('Show Points', widget.options.drawPoints, (widget, checked) => widget.options.drawPoints = checked))            
            .append(createSettingsCheckboxRow('Show Stabe', widget.options.drawStableLine, (widget, checked) => widget.options.drawStableLine = checked))
            .append(createSettingsCheckboxRow('Show Legend', widget.options.showLegend, (widget, checked) => widget.options.showLegend = checked))
            .append(createSettingsCheckboxRow('Show Time Labels', widget.options.showTimeLabels, (widget, checked) => widget.options.showTimeLabels = checked))
            ;            
        }
        return settings;        
    }

    function createPageSettings() {
        let widgetsSelection = $('<select/>');
        MonitoringConsole.Model.listSeries(function(names) {
            let lastNs;
            $.each(names, function() {
                let key = this; //.replace(/ /g, ',');
                let ns =  this.substring(3, this.indexOf(' '));
                let $option = $("<option />").val(key).text(this.substring(this.indexOf(' ')));
                if (ns == lastNs) {
                    widgetsSelection.find('optgroup').last().append($option);
                } else {
                    let group = $('<optgroup/>').attr('label', ns);
                    group.append($option);
                    widgetsSelection.append(group);
                }
                lastNs = ns;
            });
        });
        let widgetSeries = $('<input />', {type: 'text'});
        widgetsSelection.change(() => widgetSeries.val(widgetsSelection.val()));
        return createSettingsTable('settings-page')
            .append(createSettingsHeaderRow('Page'))
            .append(createSettingsRow('Name', () => $('<input/>', { type: 'text', value: MonitoringConsole.Model.Page.name() })
                .on("propertychange change keyup paste input", function() {
                    if (MonitoringConsole.Model.Page.rename(this.value)) {
                        updatePageNavigation();                        
                    }
                })))
            .append(createSettingsRow('Widgets', () => $('<span/>')
                .append(widgetsSelection)
                .append(widgetSeries)
                .append($('<button/>', {title: 'Add selected metric', text: 'Add'})
                    .click(() => onPageUpdate(MonitoringConsole.Model.Page.Widgets.add(widgetSeries.val()))))
                ));
    }

    function createDataSettings() {
        let instanceSelection = $('<select />', {multiple: true});
        $.getJSON("api/instances/", function(instances) {
            for (let i = 0; i < instances.length; i++) {
                instanceSelection.append($('<option/>', { value: instances[i], text: instances[i], selected:true}));
            }
        });
        return createSettingsTable('settings-data')
            .append(createSettingsHeaderRow('Data'))
            .append(createSettingsRow('Instances', () => instanceSelection));
    }

    function createSettingsHeaderRow(caption) {
        return $('<tr/>').append($('<th/>', {colspan: 2}).html(caption).click(function() {
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

    function createSettingsCheckboxRow(label, checked, onChange) {
        return createSettingsRow(label, () => createSettingsCheckbox(checked, onChange));
    }

    function createSettingsTable(id) {
        return $('<table />', { 'class': 'settings', id: id });
    }

    function createSettingsRow(label, createInput) {
        return $('<tr/>').append($('<td/>').text(label)).append($('<td/>').append(createInput()));   
    }

    /**
     * Creates a checkbox to configure the attributes of a widget.
     *
     * @param {boolean} isChecked - if the created checkbox should be checked
     * @param {function} onChange - a function accepting two arguments: the updated widget and the checked state of the checkbox after a change
     */
    function createSettingsCheckbox(isChecked, onChange) {
        return $("<input/>", { type: 'checkbox', checked: isChecked })
            .on('change', function() {
                let checked = this.checked;
                MonitoringConsole.Model.Page.Widgets.Selection.configure((widget) => onChange(widget, checked));
            });
    }

    function createSettingsSliderRow(label, min, max, value, onChange) {
        return createSettingsRow(label, () => $('<input/>', {type: 'number', min:min, max:max, value: value})
            .on('input change', function() {  
                let val = this.valueAsNumber;
                onPageUpdate(MonitoringConsole.Model.Page.Widgets.Selection.configure((widget) => onChange(widget, val)));
            }));
    }

    function createSettingsDropdownRow(label, options, value, onChange) {
        let dropdown = $('<select/>');
        Object.keys(options).forEach(option => dropdown.append($('<option/>', {text:options[option], value:option, selected: value === option})));
        dropdown.change(() => onPageUpdate(MonitoringConsole.Model.Page.Widgets.Selection.configure((widget) => onChange(widget, dropdown.val()))));
        return createSettingsRow(label, () => dropdown);
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~[ Event Handlers ]~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    function onWidgetToolbarClick(widget) {
        MonitoringConsole.Model.Page.Widgets.Selection.toggle(widget.series);
        onWidgetUpdate(widget);
        updatePageAndSelectionSettings();
    }

    function onWidgetDelete(series) {
        if (window.confirm('Do you really want to remove the chart from the page?')) {
            onPageUpdate(MonitoringConsole.Model.Page.Widgets.remove(series));
        }
    }

    function onPageExport(filename, text) {
        let pom = document.createElement('a');
        pom.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(text));
        pom.setAttribute('download', filename);

        if (document.createEvent) {
            let event = document.createEvent('MouseEvents');
            event.initEvent('click', true, true);
            pom.dispatchEvent(event);
        }
        else {
            pom.click();
        }
    }

    /**
     * This function is called when data was received or was failed to receive so the new data can be applied to the page.
     *
     * Depending on the update different content is rendered within a chart box.
     */
    function onDataUpdate(update) {
        let widget = update.widget;
        let boxId = widget.target + '-box';
        let box = $('#'+boxId);
        if (box.length == 0) {
            if (console && console.log)
                console.log('WARN: Box for chart ' + widget.series + ' not ready.');
            return;
        }
        let td = box.closest('.widget');
        if (update.data) {
            td.children('.status-nodata').hide();
            let points = update.data[0].points;
            let stable = points.length === 4 && points[1] === points[3] && widget.type === 'line';
            if (stable && !widget.options.drawStableLine) {
                if (td.children('.stable').length == 0) {
                    let info = $('<div/>', { 'class': 'stable' });
                    info.append($('<span/>', { text: points[1] }));
                    td.append(info);
                    box.hide();
                }
            } else {
                td.children('.stable').remove();
                box.show();
                MonitoringConsole.Chart.getAPI(widget).onDataUpdate(update);
            }
        } else {
            td.children('.status-nodata').width(box.width()-10).height(box.height()-10).show();
        }
        
        onWidgetUpdate(widget);
    }

    /**
     * Called when changes to the widget require to update the view of the widget (non data related changes)

     * TODO this should be called by the model in the same way onDataUpdate is whenever config of a widget is configured - also rename to onWidgetConfigurationUpdate?
     */
    function onWidgetUpdate(widget) {
        let container = $('#' + widget.target + '-box').closest('.widget');
        if (widget.selected) {
            container.addClass('chart-selected');
        } else {
            container.removeClass('chart-selected');
        }
    }

    /**
     * This function refleshes the page with the given layout.
     */
    function onPageUpdate(layout) {
        let numberOfColumns = layout.length;
        let maxRows = layout[0].length;
        let table = $("<table/>", { id: 'chart-grid', 'class': 'columns-'+numberOfColumns + ' rows-'+maxRows });
        let rowHeight = Math.round(($(window).height() - 100) / numberOfColumns);
        for (let row = 0; row < maxRows; row++) {
            let tr = $("<tr/>");
            for (let col = 0; col < numberOfColumns; col++) {
                let cell = layout[col][row];
                if (cell) {
                    let span = cell.span;
                    let td = $("<td/>", { colspan: span, rowspan: span, 'class': 'widget', style: 'height: '+(span * rowHeight)+"px;"});
                    td.append(createWidgetToolbar(cell));
                    let status = $('<div/>', { "class": 'status-nodata'});
                    status.append($('<div/>', {text: 'No Data'}));
                    td.append(status);
                    td.append(createWidgetTargetContainer(cell));
                    tr.append(td);
                } else if (cell === null) {
                    tr.append($("<td/>", { 'class': 'widget', style: 'height: '+rowHeight+'px;'}));                  
                }
            }
            table.append(tr);
        }
        $('#chart-container').empty();
        $('#chart-container').append(table);
    }

    /**
     * Method to call when page changes to update UI elements accordingly
     */
    function onPageChange(layout) {
        onPageUpdate(layout);
        updatePageNavigation();
        updatePageAndSelectionSettings();
    }

    /**
     * Public API of the View object:
     */
    return {
        onPageReady: function() {
            // connect the view to the model by passing the 'onDataUpdate' function to the model
            // which will call it when data is received
            onPageUpdate(MonitoringConsole.Model.init(onDataUpdate));
            updatePageAndSelectionSettings();
            updatePageNavigation();
        },
        onPageChange: (layout) => onPageChange(layout),
        onPageUpdate: (layout) => onPageUpdate(layout),
        onPageReset: () => onPageChange(MonitoringConsole.Model.Page.reset()),
        onPageImport: () => MonitoringConsole.Model.importPages(this.files, onPageChange),
        onPageExport: () => onPageExport('monitoring-console-config.json', MonitoringConsole.Model.exportPages()),
        onPageMenu: function() { MonitoringConsole.Model.Settings.toggle(); updatePageAndSelectionSettings(); },
        onPageLayoutChange: (numberOfColumns) => onPageUpdate(MonitoringConsole.Model.Page.arrange(numberOfColumns)),
        onPageDelete: function() {
            if (window.confirm("Do you really want to delete the current page?")) { 
                onPageUpdate(MonitoringConsole.Model.Page.erase());
                updatePageNavigation();
            }
        },
    };
})();
