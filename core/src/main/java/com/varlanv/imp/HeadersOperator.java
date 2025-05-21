package com.varlanv.imp;

import java.util.List;
import java.util.Map;

@FunctionalInterface
interface HeadersOperator extends ImpFn<Map<String, List<String>>, Map<String, List<String>>> {}
