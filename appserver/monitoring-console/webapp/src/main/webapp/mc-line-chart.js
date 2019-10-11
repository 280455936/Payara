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
 * Adapter to line charts of Chart.js
 */ 
MonitoringConsole.Chart.Line = (function() {
	
  const Units = MonitoringConsole.View.Units;
  const Common = MonitoringConsole.Chart.Common;

  /**
   * This is like a constant but it needs to yield new objects for each chart.
   */
  function onCreation(widget) {
    let options = {
      maintainAspectRatio: false,
      scales: {
        xAxes: [{
          type: 'time',
          gridLines: {
            color: 'rgba(100,100,100,0.3)',
            lineWidth: 0.5,
          },
          time: {
            minUnit: 'second',
            round: 'second',
          },
          ticks: {
            callback: getTimeLabel,
            minRotation: 90,
            maxRotation: 90,
          }
        }],
        yAxes: [{
          display: true,
          gridLines: {
            color: 'rgba(100,100,100,0.7)',
            lineWidth: 0.5,
          },
          ticks: {
            beginAtZero: true,
            precision:0, // no decimal places in labels
          },
        }],
      },
      legend: {
          display: false,
      }
    };
    return new Chart(widget.target, {
          type: 'line',
          data: { datasets: [], },
          options: options,
        });
  } 

  function getTimeLabel(value, index, values) {
    if (values.length == 0 || index == 0)
      return value;
    let span = values[values.length -1].value - values[0].value;
    if (span < 120000) { // less then two minutes
      let lastMinute = new Date(values[index-1].value).getMinutes();
      return new Date(values[index].value).getMinutes() != lastMinute ? value : ''+new Date(values[index].value).getSeconds();
    }
    return value;
  }

  /**
   * Convertes a array of points given as one dimensional array with alternativ time value elements 
   * to a 2-dimensional array of points with t and y attribute.
   */
  function points1Dto2D(points1d) {
    if (!points1d)
      return [];
    let points2d = new Array(points1d.length / 2);
    for (let i = 0; i < points2d.length; i++)
      points2d[i] = { t: new Date(points1d[i*2]), y: points1d[i*2+1] };
    return points2d;      
  }
    
  /**
   * Convertes a array of points given as one dimensional array with alternativ time value elements 
   * to a 2-dimensional array of points with t and y attribute where y reflects the delta between 
   * nearest 2 points of the input array. This means the result array has one less point as the input.
   */
  function points1Dto2DPerSec(points1d) {
    if (!points1d)
      return [];
    let points2d = new Array((points1d.length / 2) - 1);
    for (let i = 0; i < points2d.length; i++) {
      let t0 = points1d[i*2];
      let t1 = points1d[i*2+2];
      let y0 = points1d[i*2+1];
      let y1 = points1d[i*2+3];
      let dt = t1 - t0;
      let dy = y1 - y0;
      let y = (dt / 1000) * dy;
      points2d[i] = { t: new Date(t1), y: y };
    }
    if (points2d.length === 1)
      return [{t: new Date(points1d[0]), y: points2d[0].y}, points2d[0]];
    return points2d;
  }
	
  function createMinimumLineDataset(seriesData, points, lineColor) {
		return createHorizontalLineDataset(' min ', points, seriesData.observedMin, lineColor, [3, 3]);
  }
    
  function createMaximumLineDataset(seriesData, points, lineColor) {
  	return createHorizontalLineDataset(' max ', points, seriesData.observedMax, lineColor, [15, 3]);
  }
    
  function createAverageLineDataset(seriesData, points, lineColor) {
		return createHorizontalLineDataset(' avg ', points, seriesData.observedSum / seriesData.observedValues, lineColor, [9, 3]);
  }

  function createHorizontalLineDataset(label, points, y, lineColor, dash) {
    let line = {
      data: [{t:points[0].t, y:y}, {t:points[points.length-1].t, y:y}],
      label: label,
      fill:  false,
      borderColor: lineColor,
      borderWidth: 1,
      pointRadius: 0
    };
    if (dash)
      line.borderDash = dash;
    return line;
  }  
    
  function createCurrentLineDataset(widget, seriesData, points, lineColor, bgColor) {
		let pointRadius = widget.options.drawPoints ? 3 : 0;
    let label = seriesData.instance;
    if (widget.series.indexOf('*') > 0)
      label += ': '+ (seriesData.series.replace(new RegExp(widget.series.replace('*', '(.*)')), '$1'));
    return {
			data: points,
			label: label,
			backgroundColor: bgColor,
			borderColor: lineColor,
			borderWidth: 1,
      pointRadius: pointRadius,
		};
  }
    
  /**
   * Creates one or more lines for a single series dataset related to the widget.
   * A widget might display multiple series in the same graph generating one or more dataset for each of them.
   */
  function createSeriesDatasets(widget, seriesData) {
    let lineColor = seriesData.legend.color;
    let bgColor = seriesData.legend.backgroundColor;
    if (widget.options.perSec) {    
  		//TODO add min/max/avg per sec lines
      return [ createCurrentLineDataset(widget, seriesData, points1Dto2DPerSec(seriesData.points), lineColor, bgColor) ];
  	}
  	let points = points1Dto2D(seriesData.points);
  	let datasets = [];
  	datasets.push(createCurrentLineDataset(widget, seriesData, points, lineColor, bgColor));
  	if (points.length > 0 && widget.options.drawAvgLine) {
			datasets.push(createAverageLineDataset(seriesData, points, lineColor));
		}
		if (points.length > 0 && widget.options.drawMinLine && seriesData.observedMin > 0) {
			datasets.push(createMinimumLineDataset(seriesData, points, lineColor));
		}
		if (points.length > 0 && widget.options.drawMaxLine) {
			datasets.push(createMaximumLineDataset(seriesData, points, lineColor));
		}
    if (widget.decorations.waterline) {
      datasets.push(createHorizontalLineDataset(' waterline ', points, widget.decorations.waterline, 'Aqua', [2,2]));
    }
	  return datasets;
  }

  /**
   * Should be called whenever the configuration of the widget changes in way that needs to be transfered to the chart options.
   * Basically translates the MC level configuration options to Chart.js options
   */
  function onConfigUpdate(widget, chart) {
    let options = chart.options;
    options.elements.line.tension = widget.options.drawCurves ? 0.4 : 0;
    let time = widget.options.drawAnimations ? 1000 : 0;
    options.animation.duration = time;
    options.responsiveAnimationDuration = time;
    let rotation = widget.options.rotateTimeLabels ? 90 : undefined;
    let yAxis = options.scales.yAxes[0];
    yAxis.ticks.beginAtZero = widget.options.beginAtZero;
    let converter = Units.converter(widget.unit);
    yAxis.ticks.callback = function(value, index, values) {
      return converter.format(value, widget.unit === 'bytes');
    };
    if (widget.axis.min !== undefined)
      yAxis.ticks.suggestedMin = widget.axis.min;
    if (widget.axis.max !== undefined)
      yAxis.ticks.suggestedMax = widget.axis.max;
    let xAxis = options.scales.xAxes[0];
    xAxis.ticks.source = widget.options.autoTimeTicks ? 'auto' : 'data';
    xAxis.ticks.minRotation = rotation;
    xAxis.ticks.maxRotation = rotation;
    xAxis.ticks.display = widget.options.showTimeLabels === true;
    options.elements.line.fill = widget.options.drawFill === true;
    return chart;
  }

  function onDataUpdate(update) {
    let data = update.data;
    let widget = update.widget;
    let chart = update.chart();
    let datasets = [];
    for (let j = 0; j < data.length; j++) {
      datasets = datasets.concat(createSeriesDatasets(widget, data[j]));
    }
    chart.data.datasets = datasets;
    chart.update(0);
  }
  
  /**
   * Public API if this chart type (same for all types).
   */
	return {
    onCreation: (widget) => onConfigUpdate(widget, onCreation(widget)),
    onConfigUpdate: (widget, chart) => onConfigUpdate(widget, chart),
    onDataUpdate: (update) => onDataUpdate(update),
	};
})();
