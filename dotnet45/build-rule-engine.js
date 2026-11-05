"use strict";

const fs = require("node:fs");
const path = require("node:path");
const babel = require("@babel/core");

const sourcePath = path.join(__dirname, "..", "js", "basket_validation.js");
const outputDirectory = path.join(__dirname, "Resources");
const outputPath = path.join(outputDirectory, "basket_validation.es5.js");
const source = fs.readFileSync(sourcePath, "utf8");
const transformed = babel.transformSync(source, {
    sourceType: "script",
    comments: false,
    compact: true,
    presets: [["@babel/preset-env", { targets: { ie: "9" } }]],
}).code;

fs.mkdirSync(outputDirectory, { recursive: true });
const polyfills = `
if(!Object.values){Object.values=function(o){return Object.keys(o).map(function(k){return o[k];});};}
if(!Array.prototype.includes){Array.prototype.includes=function(v){return this.indexOf(v)!==-1;};}
if(!Array.prototype.find){Array.prototype.find=function(f){for(var i=0;i<this.length;i++){if(f(this[i],i,this))return this[i];}};}
if(!Array.from){Array.from=function(v){if(v&&v._values)return v._values.slice();return Array.prototype.slice.call(v);};}
if(typeof Set==='undefined'){var Set=function(values){this._values=[];if(values){for(var i=0;i<values.length;i++)this.add(values[i]);}};Set.prototype.add=function(v){if(this._values.indexOf(v)===-1)this._values.push(v);return this;};Set.prototype['@@iterator']=function(){var values=this._values,index=0;return{next:function(){return index<values.length?{value:values[index++],done:false}:{done:true};}};};}
`;
fs.writeFileSync(
    outputPath,
    `var module={exports:{}};var exports=module.exports;${polyfills}${transformed};` +
        `function validateBasketJson(input){return JSON.stringify(module.exports.validate_basket_helper(JSON.parse(input)));}`
);
