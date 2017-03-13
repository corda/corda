/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/target.h"

#include "codegen/compiler/regalloc.h"
#include "codegen/compiler/site.h"

namespace avian {
namespace codegen {
namespace compiler {

Value::Value(Site* site, Site* target, ir::Type type)
    : ir::Value(type),
      reads(0),
      lastRead(0),
      sites(site),
      source(0),
      target(target),
      buddy(this),
      nextWord(this),
      home(NoFrameIndex),
      wordIndex(0)
{
}

bool Value::findSite(Site* site)
{
  for (Site* s = this->sites; s; s = s->next) {
    if (s == site)
      return true;
  }
  return false;
}

bool Value::isBuddyOf(Value* b)
{
  Value* a = this;
  if (a == b)
    return true;
  for (Value* p = a->buddy; p != a; p = p->buddy) {
    if (p == b)
      return true;
  }
  return false;
}

void Value::addSite(Context* c, Site* s)
{
  if (not this->findSite(s)) {
    if (DebugSites) {
      char buffer[256];
      s->toString(c, buffer, 256);
      fprintf(stderr, "add site %s to %p\n", buffer, this);
    }
    s->acquire(c, this);
    s->next = this->sites;
    this->sites = s;
  }
}

void Value::grow(Context* c)
{
  assertT(c, this->nextWord == this);

  Value* next = value(c, this->type);
  this->nextWord = next;
  next->nextWord = this;
  next->wordIndex = 1;
}

void Value::maybeSplit(Context* c)
{
  if (this->nextWord == this) {
    this->split(c);
  }
}

void Value::split(Context* c)
{
  this->grow(c);
  for (SiteIterator it(c, this); it.hasMore();) {
    Site* s = it.next();
    this->removeSite(c, s);

    this->addSite(c, s->copyLow(c));
    this->nextWord->addSite(c, s->copyHigh(c));
  }
}

void Value::removeSite(Context* c, Site* s)
{
  for (SiteIterator it(c, this); it.hasMore();) {
    if (s == it.next()) {
      if (DebugSites) {
        char buffer[256];
        s->toString(c, buffer, 256);
        fprintf(stderr, "remove site %s from %p\n", buffer, this);
      }
      it.remove(c);
      break;
    }
  }
  if (DebugSites) {
    fprintf(stderr, "%p has more: %d\n", this, this->hasSite(c));
  }
  assertT(c, not this->findSite(s));
}

bool Value::hasSite(Context* c)
{
  SiteIterator it(c, this);
  return it.hasMore();
}

bool Value::uniqueSite(Context* c, Site* s)
{
  SiteIterator it(c, this);
  Site* p UNUSED = it.next();
  if (it.hasMore()) {
    // the site is not this word's only site, but if the site is
    // shared with the next word, it may be that word's only site
    if (this->nextWord != this
        and s->registerSize(c) > c->targetInfo.pointerSize) {
      SiteIterator nit(c, this->nextWord);
      Site* p = nit.next();
      if (nit.hasMore()) {
        return false;
      } else {
        return p == s;
      }
    } else {
      return false;
    }
  } else {
    assertT(c, p == s);
    return true;
  }
}

void Value::clearSites(Context* c)
{
  if (DebugSites) {
    fprintf(stderr, "clear sites for %p\n", this);
  }
  for (SiteIterator it(c, this); it.hasMore();) {
    it.next();
    it.remove(c);
  }
}

#ifndef NDEBUG
bool Value::hasBuddy(Context* c, Value* b)
{
  Value* a = this;
  if (a == b) {
    return true;
  }

  int i = 0;
  for (Value* p = a->buddy; p != a; p = p->buddy) {
    if (p == b) {
      return true;
    }
    if (++i > 1000) {
      abort(c);
    }
  }
  return false;
}
#endif  // not NDEBUG

Value* value(Context* c, ir::Type type, Site* site, Site* target)
{
  return new (c->zone) Value(site, target, type);
}

}  // namespace regalloc
}  // namespace codegen
}  // namespace avian
