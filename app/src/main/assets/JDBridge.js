//
// MIT License
//
// Copyright (c) 2022 JD.com, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

(function () {
    if (window.JDBridge) {
        return
    };

    const TAG = 'JDBridge: ';
    var debug = false;
    var nativeRequestQueue = [];
    var jsPlugins = {};
    var responseCallbacks = {};
    var uniqueId = 1;

    function logE(msg){
        if (debug) {
            console.error(TAG + msg);
        }
    }

    function logD(msg){
        if (debug) {
            console.log(TAG + msg);
        }
    }

    function setDebug(isDebug){
        debug = isDebug;
    }

    function isDebug(){
        return debug;
    }

    function registerDefaultPlugin(handler) {
        logD('register a defualt js plugin');
        if (handler && typeof handler != 'function') {
            logE('cannot register a default plugin that is not a function');
            return;
        }
        if (JDBridge._defaultPlugin) {
            logD('old defualt js plugin has been changed')
        };
        JDBridge._defaultPlugin = handler;
        if (nativeRequestQueue) {
            logD('dispatch request from native in queue');
            var reqQueue = nativeRequestQueue;
            nativeRequestQueue = null;
            for (var i = 0; i < reqQueue.length; i++) {
                logD('dispatch all request in queue: request = ' + JSON.stringify(reqQueue[i]));
                _doHandleFromNative(reqQueue[i]);
            }
        }
    };

    function registerPlugin(pluginName, handler) {
        logD('register a js plugin: ' + pluginName);
        if (!handler || typeof handler != 'function') {
            logE('cannot register a js plugin: ' + pluginName + ', handle is ' + handler);
            return;
        }
        jsPlugins[pluginName] = handler;
        var reqQueue = nativeRequestQueue;
        if (reqQueue && reqQueue.length > 0) {
            logD('dispatch request from native that can be handled by ' + pluginName + ' in queue(size:' + reqQueue.length + ')');
            for (var i = 0; i < reqQueue.length; i++) {
                var request = reqQueue[i];
                if (request.plugin && jsPlugins[request.plugin]) {
                    nativeRequestQueue.splice(i--, 1);
                    _doHandleFromNative(request);
                }
            }
            logD('now the native request queue size: ' + nativeRequestQueue.length);
        }
    };

    function unregisterPlugin(pluginName) {
        logD('remove a js plugin: ' + pluginName);
        delete jsPlugins[pluginName];
    };

    function callNative() {
        if (!window.XWebView) {
            logE('Error! No JDBridge native enviroment detected.');
            return;
        }
        var callParams;
        var pluginName, action, params, successFunc, errorFunc, progressFunc;
        if (arguments.length == 1) {
            var arg = arguments[0];
            if (typeof arg == 'string') {
                pluginName = arg;
            } else {
                callParams = arg;
            }
        } else if (arguments.length == 2) {
            pluginName = arguments[0];
            callParams = arguments[1];
        }
        if (callParams) {
            if (callParams.name) {
                pluginName = callParams.name;
            }
            action = callParams.action;
            params = callParams.params;
            successFunc = callParams.success;
            errorFunc = callParams.error;
            progressFunc = callParams.progress;
        }
        logD('callNative -> pluginName:' + pluginName + ', action:' + action + ', params:' + params + ', successFunc:' + successFunc + ', errorFunc:' + errorFunc + ', progressFunc:' + progressFunc);
        if (pluginName && typeof pluginName != 'string') {
            logE('Error! Plugin\'s name provided must be a string.');
            return;
        }
        var callbackId;
        if (successFunc) {
            if (typeof successFunc == 'function') {
                callbackId = 'cb_' + (uniqueId++) + '_' + new Date().getTime();
                var callbacks = {};
                responseCallbacks[callbackId] = callbacks;
                callbacks.successFunc = successFunc;
                if (errorFunc) {
                    if (typeof errorFunc == 'function') {
                        callbacks.errorFunc = errorFunc;
                    } else {
                        logE('Error! Error callback is not a function.');
                    }
                }
                if (progressFunc) {
                    if (typeof progressFunc == 'function') {
                        callbacks.progressFunc = progressFunc;
                    } else {
                        logE('Error! Progress callback is not a function.');
                    }
                }
            } else {
                logE('Error! Success callback is not a function.');
                return;
            }
        }
        var request = {
            plugin: pluginName ? pluginName : '',
            action: action ? action : '',
            params: params,
            callbackId: callbackId
        };
        window.XWebView._callNative(JSON.stringify(request));
    };

    function _doHandleFromNative(request) {
        setTimeout(function () {
            if (!window.XWebView) {
                logE('Error! No JDBridge native enviroment detected.');
                return;
            }
            var plugin;
            if (request.plugin) {
                plugin = jsPlugins[request.plugin];
            }
            if (!plugin) {
                plugin = JDBridge._defaultPlugin;
            }
            if (plugin) {
                let jsCallback;
                if (request.callbackId) {
                    const callbackId = request.callbackId;
                    logD('request.callbackId = ' + callbackId);
                    jsCallback = function (result, success, complete) {
                        if (typeof success != 'boolean' || typeof success == 'undefined') {
                            success = true;
                        }
                        if (typeof complete != 'boolean' || typeof complete == 'undefined') {
                            complete = true;
                        }
                        const response = {
                            callbackId: callbackId,
                            complete: complete
                        };
                        if (success) {
                            response.status = '0';
                            response.data = result;
                        } else {
                            response.status = '-1';
                            response.msg = result;
                        }
                        logD('response to native: ' + JSON.stringify(response));
                        const request = {
                            plugin: '_jdbridge',
                            action: '_respondFromJs',
                            params: response,
                        };
                        window.XWebView._callNative(JSON.stringify(request));
                    }
                }
                try {
                    if (plugin.length >= 2) {
                        logD('call js async plugin');
                        plugin.call(this, request.params, jsCallback);
                    } else {
                        logD('call js sync plugin');
                        var result = plugin.call(this, request.params);
                        if (jsCallback) {
                            jsCallback(result, true, true);
                        }
                    }
                } catch (exception) {
                    logE("javascript plugin threw. ", request.plugin, exception);
                }
            } else {
                logD('no plugin to handle request:' + request.plugin);
            }
        });
    };

    function _handleRequestFromNative(requestJSON) {
        logD('handle request from native: ' + requestJSON);
        var request = JSON.parse(requestJSON);
        if (!JDBridge._defaultPlugin) {
            if (request.plugin && !jsPlugins[request.plugin]) {
                logD('cannot find plugin to handle this request[' + request.plugin + '], will wait for js adding this plugin.');
                nativeRequestQueue.push(request);
                return;
            }
        }
        _doHandleFromNative(request);
    };

    function _handleResponseFromNative(responseJSON) {
        logD('handle response from native: ' + responseJSON);
        var response = JSON.parse(responseJSON);
        if (response.callbackId) {
            var callback = responseCallbacks[response.callbackId];
            if (!callback) {
                logD('cannot find the callback: ' + response.callbackId);
                return;
            }
            if (response.status == '0') {
                if (response.complete == false) {
                    if (!callback.progressFunc) {
                        logD('cannot find the progress callback: ' + response.callbackId);
                    } else {
                        callback.progressFunc(response.data, response);
                    }
                } else {
                    if (!callback.successFunc) {
                        logD('cannot find the success callback: ' + response.callbackId);
                    } else {
                        callback.successFunc(response.data, response);
                    }
                }
            } else {
                if (!callback.errorFunc) {
                    logD('cannot find the error callback: ' + response.callbackId);
                } else {
                    callback.errorFunc(response.msg, response);
                }
            }
            if (response.complete == false) {
                logD('response from native is not completed, continue to hold callback: ' + response.callbackId);
            } else {
                delete responseCallbacks[response.callbackId];
            }
        }
    };

    function nativeReady(){
        return typeof window.XWebView != 'undefined';
    }

    var JDBridge = window.JDBridge = {
        registerDefaultPlugin: registerDefaultPlugin,
        registerPlugin: registerPlugin,
        unregisterPlugin: unregisterPlugin,
        callNative: callNative,
        _handleRequestFromNative: _handleRequestFromNative,
        _handleResponseFromNative: _handleResponseFromNative,
        nativeReady: nativeReady,
        setDebug: setDebug,
        isDebug: isDebug
    };

    if (!window._jdbridgeInit) {
        logD('dispatchEvent JDBridgeReady');
        var readyEvent = new CustomEvent("JDBridgeReady", { detail: { bridge: JDBridge } });
        window.dispatchEvent(readyEvent);
        window._jdbridgeInit = true;
        setTimeout(function () {
            if (!window.XWebView) {
                logE('Error! No JDBridge native enviroment detected.');
                return;
            };
            var request = {
                plugin: '_jdbridge',
                action: '_jsInit'
            };
            window.XWebView._callNative(JSON.stringify(request));
            logD('JDBridge is Ready.');
        });
    };
})()
