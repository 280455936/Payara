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

    const Components = MonitoringConsole.View.Components;
    const Units = MonitoringConsole.View.Units;

    /**
     * Updates the DOM with the page navigation tabs so it reflects current model state
     */ 
    function updatePageNavigation() {
        let nav = { 
            onChange: function(pageid) {
                MonitoringConsole.Chart.Trace.onClosePopup();
                onPageChange(MonitoringConsole.Model.Page.changeTo(pageid));
            },
            pages: MonitoringConsole.Model.listPages().map(function(page) {
                return { label: page.name, id: page.id, active: page.active };
            }),
        };
        Components.onNavigationUpdate(nav);
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
            let settings = [];
            settings.push(createPageSettings());
            settings.push(createDataSettings());
            if (MonitoringConsole.Model.Page.Widgets.Selection.isSingle()) {
                settings = settings.concat(createWidgetSettings(MonitoringConsole.Model.Page.Widgets.Selection.first()));
            }
            Components.onSettingsUpdate(settings);
        } else {
            panelConsole.removeClass('state-show-settings');
        }
    }

    function createWidgetLegend(widget) {
        return $('<ol/>',  {'class': 'widget-legend-bar'});
    }

    function createWidgetLegendItem(data, color) {
        let value = data.points[data.points.length-1];
        return $('<li/>', {style: 'border-color: '+color+';'}).append($('<span/>').text(data.instance)).append($('<span/>').text(value));
    }

    function updateDomOfWidget(parent, widget) {
        if (!parent) {
            parent = $('#widget-'+widget.target);
            if (!parent) {
                return; // can't update
            }
        }
        if (parent.children().length == 0) {
            let previousParent = $('#widget-'+widget.target);
            if (previousParent.length > 0 && previousParent.children().length > 0) {
                previousParent.children().appendTo(parent);
            } else {
                parent.append(createWidgetToolbar(widget));
                parent.append(createWidgetTargetContainer(widget));
                parent.append(Components.onLegendCreation([]));                
            }
        }
        if (widget.selected) {
            parent.addClass('chart-selected');
        } else {
            parent.removeClass('chart-selected');
        }
    }

    /**
     * Each chart needs to be in a relative positioned box to allow responsive sizing.
     * This fuction creates this box including the canvas element the chart is drawn upon.
     */
    function createWidgetTargetContainer(widget) {
        return $('<div/>', { id: widget.target + '-box', "class": "widget-chart-box" })
            .append($('<canvas/>',{ id: widget.target }));
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
        let metricText = toWords(metric);
        let grouped = false;
        for (let i = 0; i < tags.length; i++) {
            let tag = tags[i];
            if (tag.startsWith('@:')) {
                grouped = true;
                text += metricText;
                text += ': <code>'+tag.substring(2)+'</code> ';
            } else {
                text +=' <i>'+tag+'</i> ';
            }
        }
        if (!grouped)
            text += metricText;
        return text;
    }

    function createWidgetToolbar(widget, expanded) {
        let series = widget.series;
        let settings = $('<span/>', {'class': 'widget-settings-bar', style: expanded ? 'display: inline;' : ''})
            .append(createToolbarButton('Remove chart from page', '&times; Remove', () => onWidgetDelete(series)))
            .append(createToolbarButton('Enlarge this chart', '&ltri;&rtri; Larger', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.spanMore(series))))
            .append(createToolbarButton('Shrink this chart', '&rtri;&ltri; Smaller', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.spanLess(series))))
            .append(createToolbarButton('Move to right', '&rtri; Move Right', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.moveRight(series))))
            .append(createToolbarButton('Move to left', '&ltri; Move Left', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.moveLeft(series))))
            .append(createToolbarButton('Move up', '&triangle; Move Up', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.moveUp(series))))
            .append(createToolbarButton('Move down', '&dtri; Move Down', () => onPageUpdate(MonitoringConsole.Model.Page.Widgets.moveDown(series))))
            .append(createToolbarButton('Open in Side Panel', '&#9881; More...', () => onOpenWidgetSettings(series)))
            ;
        return $('<div/>', {"class": "widget-title-bar"})
            .append($('<h3/>', {title: 'Select '+series}).html(formatSeriesName(widget))
                .click(() => onWidgetToolbarClick(widget)))
            .append(createToolbarButton('Settings', '&#9881;', () => $(settings).toggle())
            .append(settings));
    }

    function createToolbarButton(title, icon, onClick) {
        return $('<button/>', { "class": "btnIcon", title: title}).html(icon).click(onClick);
    }


    function createWidgetSettings(widget) {
        let options = widget.options;
        let unit = widget.unit;
        let thresholds = widget.decorations.thresholds;
        let settings = [];
        settings.push({ id: 'settings-widget', caption: 'Widget', entries: [
            { label: 'Type', type: 'dropdown', options: {line: 'Time Curve', bar: 'Range Indicator'}, value: widget.type, onChange: (widget, selected) => widget.type = selected},
            { label: 'Column / Item', input: [
                { type: 'range', min: 1, max: 4, value: 1 + (widget.grid.column || 0), onChange: (widget, value) => widget.grid.column = value - 1},
                { type: 'range', min: 1, max: 4, value: 1 + (widget.grid.item || 0), onChange: (widget, value) => widget.grid.item = value - 1},
            ]},             
            { label: 'Span', type: 'range', min: 1, max: 4, value: widget.grid.span || 1, onChange: (widget, value) => widget.grid.span = value},
        ]});
        settings.push({ id: 'settings-data', caption: 'Data', entries: [
            { label: 'Unit', input: [
                { type: 'dropdown', options: {count: 'Count', ms: 'Milliseconds', ns: 'Nanoseconds', bytes: 'Bytes', percent: 'Percentage'}, value: widget.unit, onChange: (widget, selected) => widget.unit = selected},
                { label: '1/sec', type: 'checkbox', value: options.perSec, onChange: (widget, checked) => options.perSec = checked},
            ]},
            { label: 'Extra Lines', input: [
                { label: 'Min', type: 'checkbox', value: options.drawMinLine, onChange: (widget, checked) => options.drawMinLine = checked},
                { label: 'Max', type: 'checkbox', value: options.drawMaxLine, onChange: (widget, checked) => options.drawMaxLine = checked},
                { label: 'Avg', type: 'checkbox', value: options.drawAvgLine, onChange: (widget, checked) => options.drawAvgLine = checked},            
            ]},
            { label: 'Display', input: [
                { label: 'Points', type: 'checkbox', value: options.drawPoints, onChange: (widget, checked) => options.drawPoints = checked },
                { label: 'Fill', type: 'checkbox', value: !options.noFill, onChange: (widget, checked) => options.noFill = !checked},
                { label: 'Curvy', type: 'checkbox', value: options.drawCurves, onChange: (widget, checked) => options.drawCurves = checked},
            ]},
            { label: 'X-Axis', input: [
                { label: 'Labels', type: 'checkbox', value: !options.noTimeLabels, onChange: (widget, checked) => options.noTimeLabels = !checked},
            ]},            
            { label: 'Y-Axis', input: [
                { label: 'Min', type: 'value', unit: unit, value: widget.axis.min, onChange: (widget, value) => widget.axis.min = value},
                { label: 'Max', type: 'value', unit: unit, value: widget.axis.max, onChange: (widget, value) => widget.axis.max = value},
            ]},
        ]});
        settings.push({ id: 'settings-decorations', caption: 'Decorations', entries: [
            { label: 'Waterline', type: 'value', unit: unit, value: widget.decorations.waterline, onChange: (widget, value) => widget.decorations.waterline = value },
            { label: 'Threshold Reference', type: 'dropdown', options: { off: 'Off', now: 'Most Recent Value', min: 'Minimum Value', max: 'Maximum Value', avg: 'Average Value'}, value: thresholds.reference, onChange: (widget, selected) => thresholds.reference = selected},
            { label: 'Alarming Threshold', input: [
                { type: 'value', unit: unit, value: thresholds.alarming.value, onChange: (widget, value) => thresholds.alarming.value = value },
                { label: 'Line', type: 'checkbox', value: thresholds.alarming.display, onChange: (widget, checked) => thresholds.alarming.display = checked },
            ]},
            { label: 'Critical Threshold', input: [
                { type: 'value', unit: unit, value: thresholds.critical.value, onChange: (widget, value) => thresholds.critical.value = value },
                { label: 'Line', type: 'checkbox', value: thresholds.critical.display, onChange: (widget, checked) => thresholds.critical.display = checked },
            ]},                
            //TODO add color for each threshold
        ]});
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
        
        return { id: 'settings-page', caption: 'Page', entries: [
            { label: 'Name', input: () => 
                $('<input/>', { type: 'text', value: MonitoringConsole.Model.Page.name() })
                .on("propertychange change keyup paste input", function() {
                    if (MonitoringConsole.Model.Page.rename(this.value)) {
                        updatePageNavigation();                        
                    }
                })
            },
            { label: 'Widgets', input: () => 
                $('<span/>')
                .append(widgetsSelection)
                .append(widgetSeries)
                .append($('<button/>', {title: 'Add selected metric', text: 'Add'})
                    .click(() => onPageUpdate(MonitoringConsole.Model.Page.Widgets.add(widgetSeries.val()))))
            },
        ]};
    }

    function createDataSettings() {
        let instanceSelection = $('<select />', {multiple: true});
        $.getJSON("api/instances/", function(instances) {
            for (let i = 0; i < instances.length; i++) {
                instanceSelection.append($('<option/>', { value: instances[i], text: instances[i], selected:true}));
            }
        });
        return { id: 'settings-data', caption: 'Data', entries: [
            { label: 'Instances', input: instanceSelection }
        ]};
    }

    

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~[ Event Handlers ]~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    function onWidgetToolbarClick(widget) {
        MonitoringConsole.Model.Page.Widgets.Selection.toggle(widget.series);
        updateDomOfWidget(undefined, widget);
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

    function createLegend(widget, data) {
        if (!data)
            return [{ label: 'No Data', value: '?', color: 'red' }];
        let legend = [];
        let format = Units.converter(widget.unit).format;
        for (let j = 0; j < data.length; j++) {
            let seriesData = data[j];
            let label = seriesData.instance;
            if (widget.series.indexOf('*') > 0) {
                label = seriesData.series.replace(new RegExp(widget.series.replace('*', '(.*)')), '$1').replace('_', ' ');
            }
            let value = format(seriesData.points[seriesData.points.length-1], widget.unit === 'bytes');
            if (widget.options.perSec)
                value += ' /s';
            let item = { 
                label: label, 
                value: value, 
                color: MonitoringConsole.Chart.Common.lineColor(j),
                backgroundColor: MonitoringConsole.Chart.Common.backgroundColor(j),
                assessments: seriesData.assessments,
            };
            legend.push(item);
            data[j].legend = item;
        }
        return legend;
    }

    /**
     * This function is called when data was received or was failed to receive so the new data can be applied to the page.
     *
     * Depending on the update different content is rendered within a chart box.
     */
    function onDataUpdate(update) {
        let widget = update.widget;
        updateDomOfWidget(undefined, widget);
        let widgetNode = $('#widget-'+widget.target);
        let legendNode = widgetNode.find('.widget-legend-bar').first();
        let legend = createLegend(widget, update.data); // OBS this has side effect of setting .legend attribute in series data
        if (update.data) {
            MonitoringConsole.Chart.getAPI(widget).onDataUpdate(update);
        }
        legendNode.replaceWith(Components.onLegendCreation(legend));
    }

    /**
     * This function refleshes the page with the given layout.
     */
    function onPageUpdate(layout) {
        let numberOfColumns = layout.length;
        let maxRows = layout[0].length;
        let table = $("<table/>", { id: 'chart-grid', 'class': 'columns-'+numberOfColumns + ' rows-'+maxRows });
        let rowHeight = Math.round(($(window).height() - 100) / maxRows) - 30; // padding is subtracted
        for (let row = 0; row < maxRows; row++) {
            let tr = $("<tr/>");
            for (let col = 0; col < numberOfColumns; col++) {
                let cell = layout[col][row];
                if (cell) {
                    let span = cell.span;
                    let td = $("<td/>", { id: 'widget-'+cell.widget.target, colspan: span, rowspan: span, 'class': 'widget', style: 'height: '+(span * rowHeight)+"px;"});
                    updateDomOfWidget(td, cell.widget);
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

    function onOpenWidgetSettings(series) {
        MonitoringConsole.Model.Page.Widgets.Selection.clear();
        MonitoringConsole.Model.Page.Widgets.Selection.toggle(series);
        MonitoringConsole.Model.Settings.open();
        updatePageAndSelectionSettings();
    }

    /**
     * Public API of the View object:
     */
    return {
        Units: Units,
        Components: Components,
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
        onPageImport: (src) => MonitoringConsole.Model.importPages(src, onPageChange),
        onPageExport: () => onPageExport('monitoring-console-config.json', MonitoringConsole.Model.exportPages()),
        onPageMenu: function() { MonitoringConsole.Model.Settings.toggle(); updatePageAndSelectionSettings(); },
        onPageLayoutChange: (numberOfColumns) => onPageUpdate(MonitoringConsole.Model.Page.arrange(numberOfColumns)),
        onPageDelete: function() {
            if (window.confirm("Do you really want to delete the current page?")) { 
                onPageUpdate(MonitoringConsole.Model.Page.erase());
                updatePageNavigation();
            }
        },
        onOpenWidgetSettings: (series) => onOpenWidgetSettings(series),
    };
})();
