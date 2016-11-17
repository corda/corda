/**
 * @license
 * Copyright Google Inc. All Rights Reserved.
 *
 * Use of this source code is governed by an MIT-style license that can be
 * found in the LICENSE file at https://angular.io/license
 */
export class Tree {
    constructor(root) {
        this._root = root;
    }
    get root() { return this._root.value; }
    parent(t) {
        const p = this.pathFromRoot(t);
        return p.length > 1 ? p[p.length - 2] : null;
    }
    children(t) {
        const n = findNode(t, this._root);
        return n ? n.children.map(t => t.value) : [];
    }
    firstChild(t) {
        const n = findNode(t, this._root);
        return n && n.children.length > 0 ? n.children[0].value : null;
    }
    siblings(t) {
        const p = findPath(t, this._root, []);
        if (p.length < 2)
            return [];
        const c = p[p.length - 2].children.map(c => c.value);
        return c.filter(cc => cc !== t);
    }
    pathFromRoot(t) { return findPath(t, this._root, []).map(s => s.value); }
    contains(tree) { return contains(this._root, tree._root); }
}
function findNode(expected, c) {
    if (expected === c.value)
        return c;
    for (let cc of c.children) {
        const r = findNode(expected, cc);
        if (r)
            return r;
    }
    return null;
}
function findPath(expected, c, collected) {
    collected.push(c);
    if (expected === c.value)
        return collected;
    for (let cc of c.children) {
        const cloned = collected.slice(0);
        const r = findPath(expected, cc, cloned);
        if (r)
            return r;
    }
    return [];
}
function contains(tree, subtree) {
    if (tree.value !== subtree.value)
        return false;
    for (let subtreeNode of subtree.children) {
        const s = tree.children.filter(child => child.value === subtreeNode.value);
        if (s.length === 0)
            return false;
        if (!contains(s[0], subtreeNode))
            return false;
    }
    return true;
}
export class TreeNode {
    constructor(value, children) {
        this.value = value;
        this.children = children;
    }
    toString() { return `TreeNode(${this.value})`; }
}
//# sourceMappingURL=tree.js.map