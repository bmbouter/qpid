/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _SessionContext_
#define _SessionContext_

#include "qpid/framing/OutputHandler.h"

namespace qpid {
namespace io {

/**
 * Provides the output handler associated with a connection.
 */
class SessionContext : public virtual qpid::framing::OutputHandler 
{
  public:
    virtual void close() = 0;
};

}}


#endif
