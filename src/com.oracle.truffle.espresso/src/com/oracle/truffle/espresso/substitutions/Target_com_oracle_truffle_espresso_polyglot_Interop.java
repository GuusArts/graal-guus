package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_Interop {

    private static final InteropLibrary UNCACHED = InteropLibrary.getUncached();

    /**
     * Returns <code>true</code> if the receiver represents a <code>null</code> like value, else
     * <code>false</code>. Most object oriented languages have one or many values representing null
     * values. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isNull(Object)
     */
    @Substitution
    public static boolean isNull(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isNull(unwrap(receiver));
    }

    // region Boolean Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>boolean</code> like value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isBoolean(Object)
     */
    @Substitution
    public static boolean isBoolean(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isBoolean(unwrap(receiver));
    }

    /**
     * Returns the Java boolean value if the receiver represents a
     * {@link InteropLibrary#isBoolean(StaticObject) boolean} like value.
     *
     * @see InteropLibrary#asBoolean(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static boolean asBoolean(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asBoolean(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Boolean Messages

    // region String Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>string</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isString(Object)
     */
    @Substitution
    public static boolean isString(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isString(unwrap(receiver));
    }

    /**
     * Returns the Java string value if the receiver represents a
     * {@link InteropLibrary#isString(StaticObject) string} like value.
     *
     * @see InteropLibrary#asString(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(String.class) StaticObject asString(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return meta.toGuestString(UNCACHED.asString(unwrap(receiver)));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion String Messages

    // region Number Messages

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> value, else
     * <code>false</code>. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#isNumber(Object)
     */
    @Substitution
    public static boolean isNumber(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isNumber(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java byte primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInByte(Object)
     */
    @Substitution
    public static boolean fitsInByte(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInByte(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java short primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInShort(Object)
     */
    @Substitution
    public static boolean fitsInShort(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInShort(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java int primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInInt(Object)
     */
    @Substitution
    public static boolean fitsInInt(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInInt(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java long primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInLong(Object)
     */
    @Substitution
    public static boolean fitsInLong(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInLong(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java float primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInFloat(Object)
     */
    @Substitution
    public static boolean fitsInFloat(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInFloat(unwrap(receiver));
    }

    /**
     * Returns <code>true</code> if the receiver represents a <code>number</code> and its value fits
     * in a Java double primitive without loss of precision, else <code>false</code>. Invoking this
     * message does not cause any observable side-effects.
     *
     * @see InteropLibrary#fitsInDouble(Object)
     */
    @Substitution
    public static boolean fitsInDouble(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.fitsInDouble(unwrap(receiver));
    }

    /**
     * Returns the receiver value as Java byte primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asByte(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static byte asByte(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asByte(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java short primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asShort(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static short asShort(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asShort(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java int primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asInt(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static int asInt(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asInt(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java long primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asLong(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static long asLong(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asLong(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java float primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asFloat(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static float asFloat(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asFloat(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the receiver value as Java double primitive if the number fits without loss of
     * precision. Invoking this message does not cause any observable side-effects.
     *
     * @see InteropLibrary#asDouble(Object)
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static double asDouble(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.asDouble(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Number Messages

    // region Exception Messages

    /**
     * Returns <code>true</code> if the receiver value represents a throwable exception/error}.
     * Invoking this message does not cause any observable side-effects. Returns <code>false</code>
     * by default.
     * <p>
     * Objects must only return <code>true</code> if they support
     * {@link InteropLibrary#throwException} as well. If this method is implemented then also
     * {@link InteropLibrary#throwException(Object)} must be implemented.
     * <p>
     * The following simplified {@code TryCatchNode} shows how the exceptions should be handled by
     * languages.
     *
     * @see InteropLibrary#isException(Object)
     * @since 19.3
     */
    @Substitution
    public static boolean isException(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isException(unwrap(receiver));
    }

    /**
     * Throws the receiver object as an exception of the source language, as if it was thrown by the
     * source language itself. Allows rethrowing exceptions caught by another language. If this
     * method is implemented then also {@link InteropLibrary#isException(Object)} must be
     * implemented.
     * <p>
     * Any interop value can be an exception value and export
     * {@link InteropLibrary#throwException(Object)}. The exception thrown by this message must
     * extend {@link com.oracle.truffle.api.exception.AbstractTruffleException}. In future versions
     * this contract will be enforced using an assertion.
     *
     * @see InteropLibrary#throwException(Object)
     * @since 19.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(RuntimeException.class) StaticObject throwException(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            throw UNCACHED.throwException(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@link ExceptionType exception type} of the receiver. Throws
     * {@code UnsupportedMessageException} when the receiver is not an exception.
     *
     * @see InteropLibrary#getExceptionType(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(typeName = "Lcom/oracle/truffle/espresso/polyglot/ExceptionType;") StaticObject getExceptionType(
                    @Host(Object.class) StaticObject receiver,
                    @InjectMeta Meta meta) {
        try {
            ExceptionType exceptionType = UNCACHED.getExceptionType(unwrap(receiver));
            StaticObject staticStorage = meta.polyglot.ExceptionType.tryInitializeAndGetStatics();
            // @formatter:off
            switch (exceptionType) {
                case EXIT          : return (StaticObject) meta.polyglot.ExceptionType_EXIT.get(staticStorage);
                case INTERRUPT     : return (StaticObject) meta.polyglot.ExceptionType_INTERRUPT.get(staticStorage);
                case RUNTIME_ERROR : return (StaticObject) meta.polyglot.ExceptionType_RUNTIME_ERROR.get(staticStorage);
                case PARSE_ERROR   : return (StaticObject) meta.polyglot.ExceptionType_PARSE_ERROR.get(staticStorage);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("Unexpected ExceptionType: ", exceptionType);
            }
            // @formatter:on
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if receiver value represents an incomplete source exception. Throws
     * {@code UnsupportedMessageException} when the receiver is not an
     * {@link InteropLibrary#isException(Object) exception} or the exception is not a
     * {@link ExceptionType#PARSE_ERROR}.
     *
     * @see InteropLibrary#isExceptionIncompleteSource(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static boolean isExceptionIncompleteSource(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.isExceptionIncompleteSource(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns exception exit status of the receiver. Throws {@code UnsupportedMessageException}
     * when the receiver is not an {@link InteropLibrary#isException(Object) exception} of the
     * {@link ExceptionType#EXIT exit type}. A return value zero indicates that the execution of the
     * application was successful, a non-zero value that it failed. The individual interpretation of
     * non-zero values depends on the application.
     *
     * @see InteropLibrary#getExceptionExitStatus(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static int getExceptionExitStatus(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.getExceptionExitStatus(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception with an attached internal cause.
     * Invoking this message does not cause any observable side-effects. Returns {@code false} by
     * default.
     *
     * @see InteropLibrary#hasExceptionCause(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionCause(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionCause(unwrap(receiver));
    }

    /**
     * Returns the internal cause of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an {@link InteropLibrary#isException(Object) exception} or has no
     * internal cause. The return value of this message is guaranteed to return <code>true</code>
     * for {@link InteropLibrary#isException(Object)}.
     *
     * @see InteropLibrary#getExceptionCause(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionCause(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object cause = UNCACHED.getExceptionCause(unwrap(receiver));
            assert UNCACHED.isException(cause);
            assert !UNCACHED.isNull(cause);
            if (cause instanceof StaticObject) {
                return (StaticObject) cause; // Already typed, do not re-type.
            }
            // The cause must be an exception, wrap it as ForeignException.
            return StaticObject.createForeign(meta.polyglot.ForeignException, cause, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception that has an exception message. Invoking
     * this message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#hasExceptionMessage(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionMessage(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionMessage(unwrap(receiver));
    }

    /**
     * Returns exception message of the receiver. Throws {@code UnsupportedMessageException} when
     * the receiver is not an exception or has no exception message. The return value of this
     * message is guaranteed to return <code>true</code> for
     * {@link InteropLibrary#isString(Object)}.
     *
     * @see InteropLibrary#getExceptionMessage(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionMessage(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object message = UNCACHED.getExceptionMessage(unwrap(receiver));
            assert UNCACHED.isString(message);
            if (message instanceof StaticObject) {
                return (StaticObject) message;
            }
            // TODO(peterssen): Cannot wrap as String even if the foreign object is String-like.
            // Executing String methods, that rely on it having a .value field is not supported yet
            // in Espresso.
            return StaticObject.createForeign(meta.java_lang_Object, message, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns {@code true} if the receiver is an exception and has a stack trace. Invoking this
     * message does not cause any observable side-effects. Returns {@code false} by default.
     *
     * @see InteropLibrary#hasExceptionStackTrace(Object)
     * @since 20.3
     */
    @Substitution
    public static boolean hasExceptionStackTrace(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasExceptionStackTrace(unwrap(receiver));
    }

    /**
     * Returns the exception stack trace of the receiver that is of type exception. Returns an
     * {@link InteropLibrary#hasArrayElements(Object) array} of objects with potentially
     * {@link InteropLibrary#hasExecutableName(Object) executable name},
     * {@link InteropLibrary#hasDeclaringMetaObject(Object) declaring meta object} and
     * {@link InteropLibrary#hasSourceLocation(Object) source location} of the caller. Throws
     * {@code UnsupportedMessageException} when the receiver is not an
     * {@link InteropLibrary#isException(Object) exception} or has no stack trace. Invoking this
     * message or accessing the stack trace elements array must not cause any observable
     * side-effects.
     *
     * @see InteropLibrary#getExceptionStackTrace(Object)
     * @since 20.3
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getExceptionStackTrace(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object stackTrace = UNCACHED.getExceptionStackTrace(unwrap(receiver));
            if (stackTrace instanceof StaticObject) {
                return (StaticObject) stackTrace;
            }
            // Return foreign object as an opaque j.l.Object.
            return StaticObject.createForeign(meta.java_lang_Object, stackTrace, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Exception Messages

    // region Array Messages

    /**
     * Returns <code>true</code> if the receiver may have array elements. Therefore, At least one of
     * {@link InteropLibrary#readArrayElement(Object, long)},
     * {@link InteropLibrary#writeArrayElement(Object, long, Object)},
     * {@link InteropLibrary#removeArrayElement(Object, long)} must not throw {#link
     * {@link UnsupportedMessageException}. For example, the contents of an array or list
     * datastructure could be interpreted as array elements. Invoking this message does not cause
     * any observable side-effects. Returns <code>false</code> by default.
     *
     * @see InteropLibrary#hasArrayElements(Object)
     * @since 19.0
     */
    @Substitution
    public static boolean hasArrayElements(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasArrayElements(unwrap(receiver));
    }

    /**
     * Reads the value of an array element by index. This method must have not observable
     * side-effect.
     *
     * @see InteropLibrary#readArrayElement(Object, long)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, InvalidArrayIndexException.class})
    public static @Host(Object.class) StaticObject readArrayElement(@Host(Object.class) StaticObject receiver, long index, @InjectMeta Meta meta) {
        try {
            Object value = UNCACHED.readArrayElement(unwrap(receiver), index);
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            // The foreign object *could* be wrapped into a more precise Java type, but inferring
            // this Java type
            // from the interop "kind" (string, primitive, exception, array...) is ambiguous and
            // inefficient.
            // The caller is responsible to re-wrap or convert the result as needed.
            return StaticObject.createForeign(meta.java_lang_Object, value, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the array size of the receiver.
     *
     * @see InteropLibrary#getArraySize(Object)
     * @since 19.0
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static long getArraySize(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.getArraySize(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given array element is
     * {@link InteropLibrary#readArrayElement(Object, long) readable}. This method may only return
     * <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementReadable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementReadable(@Host(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementReadable(unwrap(receiver), index);
    }

    /**
     * Writes the value of an array element by index. Writing an array element is allowed if is
     * existing and {@link InteropLibrary#isArrayElementModifiable(Object, long) modifiable}, or not
     * existing and {@link InteropLibrary#isArrayElementInsertable(Object, long) insertable}.
     * <p>
     * This method must have not observable side-effects other than the changed array element.
     *
     * @see InteropLibrary#writeArrayElement(Object, long, Object)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnsupportedTypeException.class, InvalidArrayIndexException.class})
    public static void writeArrayElement(@Host(Object.class) StaticObject receiver, long index, @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        try {
            if (receiver.isEspressoObject()) {
                // Do not throw away the types if the receiver is an Espresso object.
                UNCACHED.writeArrayElement(receiver, index, value);
            } else {
                // Write to foreign array, full unwrap.
                UNCACHED.writeArrayElement(unwrap(receiver), index, unwrap(value));
            }
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Remove an array element from the receiver object. Removing member is allowed if the array
     * element is {@link InteropLibrary#isArrayElementRemovable(Object, long) removable}. This
     * method may only return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)}
     * returns <code>true</code> as well and
     * {@link InteropLibrary#isArrayElementInsertable(Object, long)} returns <code>false</code>.
     * <p>
     * This method does not have observable side-effects other than the removed array element and
     * shift of remaining elements. If shifting is not supported then the array might allow only
     * removal of last element.
     *
     * @see InteropLibrary#removeArrayElement(Object, long)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, InvalidArrayIndexException.class})
    public static void removeArrayElement(@Host(Object.class) StaticObject receiver, long index, @InjectMeta Meta meta) {
        try {
            UNCACHED.removeArrayElement(unwrap(receiver), index);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link InteropLibrary#writeArrayElement(Object, long, Object) writable}. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isArrayElementInsertable(Object, long)}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementModifiable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementModifiable(@Host(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementModifiable(unwrap(receiver), index);
    }

    /**
     * Returns <code>true</code> if a given array element index is not existing and
     * {@link InteropLibrary#writeArrayElement(Object, long, Object) insertable}. This method may
     * only return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isArrayElementExisting(Object, long)}}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementInsertable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementInsertable(@Host(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementModifiable(unwrap(receiver), index);
    }

    /**
     * Returns <code>true</code> if a given array element index is existing and
     * {@link InteropLibrary#removeArrayElement(Object, long) removable}. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasArrayElements(Object)} returns
     * <code>true</code> as well and {@link InteropLibrary#isArrayElementInsertable(Object, long)}}
     * returns <code>false</code>. Invoking this message does not cause any observable side-effects.
     * Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isArrayElementRemovable(Object, long)
     * @since 19.0
     */
    @Substitution
    public static boolean isArrayElementRemovable(@Host(Object.class) StaticObject receiver, long index) {
        return UNCACHED.isArrayElementRemovable(unwrap(receiver), index);
    }

    // endregion Array Messages

    // region MetaObject Messages

    /**
     * Returns <code>true</code> if the receiver value has a metaobject associated. The metaobject
     * represents a description of the object, reveals its kind and its features. Some information
     * that a metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. Should return <code>false</code> when no metaobject is known for this type.
     * Returns <code>false</code> by default.
     * <p>
     * An example, for Java objects the returned metaobject is the {@link Object#getClass() class}
     * instance. In JavaScript this could be the function or class that is associated with the
     * object.
     * <p>
     * Metaobjects for primitive values or values of other languages may be provided using language
     * views. While an object is associated with a metaobject in one language, the metaobject might
     * be a different when viewed from another language.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#getMetaObject(Object)} must be implemented.
     *
     * @see InteropLibrary#hasMetaObject(Object)
     * @since 20.1
     */
    @Substitution
    public static boolean hasMetaObject(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasMetaObject(unwrap(receiver));
    }

    /**
     * Returns the metaobject that is associated with this value. The metaobject represents a
     * description of the object, reveals its kind and its features. Some information that a
     * metaobject might define includes the base object's type, interface, class, methods,
     * attributes, etc. When no metaobject is known for this type. Throws
     * {@link UnsupportedMessageException} by default.
     * <p>
     * The returned object must return <code>true</code> for
     * {@link InteropLibrary#isMetaObject(Object)} and provide implementations for
     * {@link InteropLibrary#getMetaSimpleName(Object)},
     * {@link InteropLibrary#getMetaQualifiedName(Object)}, and
     * {@link InteropLibrary#isMetaInstance(Object, Object)}. For all values with metaobjects it
     * must at hold that <code>isMetaInstance(getMetaObject(value), value) ==
     * true</code>.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#hasMetaObject(Object)} must be implemented.
     *
     * @see InteropLibrary#hasMetaObject(Object)
     * @since 20.1
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMetaObject(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            Object metaObject = UNCACHED.getMetaObject(unwrap(receiver));
            if (metaObject instanceof StaticObject) {
                return (StaticObject) metaObject;
            }
            return StaticObject.createForeign(meta.java_lang_Object, metaObject, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Converts the receiver to a human readable {@link InteropLibrary#isString(Object) string}.
     * Each language may have special formating conventions - even primitive values may not follow
     * the traditional Java rules. The format of the returned string is intended to be interpreted
     * by humans not machines and should therefore not be relied upon by machines. By default the
     * receiver class name and its {@link System#identityHashCode(Object) identity hash code} is
     * used as string representation.
     *
     * @param allowSideEffects whether side-effects are allowed in the production of the string.
     * @since 20.1
     */
    @Substitution
    public static @Host(Object.class) StaticObject toDisplayString(@Host(Object.class) StaticObject receiver, boolean allowSideEffects, @InjectMeta Meta meta) {
        Object displayString = UNCACHED.toDisplayString(unwrap(receiver), allowSideEffects);
        if (displayString instanceof StaticObject) {
            return (StaticObject) displayString;
        }
        return StaticObject.createForeign(meta.java_lang_Object, displayString, UNCACHED);
    }

    /**
     * Converts the receiver to a human readable {@link InteropLibrary#isString(Object) string} of
     * the language. Short-cut for
     * <code>{@link InteropLibrary#toDisplayString(Object) toDisplayString}(true)</code>.
     *
     * @see InteropLibrary#toDisplayString(Object, boolean)
     * @since 20.1
     */
    public static @Host(Object.class) StaticObject toDisplayString(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        return toDisplayString(receiver, true, meta);
    }

    /**
     * Returns <code>true</code> if the receiver value represents a metaobject. Metaobjects may be
     * values that naturally occur in a language or they may be returned by
     * {@link InteropLibrary#getMetaObject(Object)}. A metaobject represents a description of the
     * object, reveals its kind and its features. If a receiver is a metaobject it is often also
     * {@link InteropLibrary#isInstantiable(Object) instantiable}, but this is not a requirement.
     * <p>
     * <b>Sample interpretations:</b> In Java an instance of the type {@link Class} is a metaobject.
     * In JavaScript any function instance is a metaobject. For example, the metaobject of a
     * JavaScript class is the associated constructor function.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#getMetaQualifiedName(Object)},
     * {@link InteropLibrary#getMetaSimpleName(Object)} and
     * {@link InteropLibrary#isMetaInstance(Object, Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Substitution
    public static boolean isMetaObject(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.isMetaObject(unwrap(receiver));
    }

    /**
     * Returns the qualified name of a metaobject as {@link InteropLibrary#isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The qualified name of a Java class includes the package name
     * and its class name. JavaScript does not have the notion of qualified name and therefore
     * returns the {@link InteropLibrary#getMetaSimpleName(Object) simple name} instead.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#isMetaObject(Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMetaQualifiedName(@Host(Object.class) StaticObject metaObject, @InjectMeta Meta meta) {
        try {
            Object qualifiedName = UNCACHED.getMetaQualifiedName(unwrap(metaObject));
            if (qualifiedName instanceof StaticObject) {
                return (StaticObject) qualifiedName;
            }
            return StaticObject.createForeign(meta.java_lang_Object, qualifiedName, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns the simple name of a metaobject as {@link InteropLibrary#isString(Object) string}.
     * <p>
     * <b>Sample interpretations:</b> The simple name of a Java class is the class name.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#isMetaObject(Object)} must be implemented as well.
     *
     * @since 20.1
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMetaSimpleName(@Host(Object.class) StaticObject metaObject, @InjectMeta Meta meta) {
        try {
            Object simpleName = UNCACHED.getMetaSimpleName(unwrap(metaObject));
            if (simpleName instanceof StaticObject) {
                return (StaticObject) simpleName;
            }
            return StaticObject.createForeign(meta.java_lang_Object, simpleName, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if the given instance is of the provided receiver metaobject, else
     * <code>false</code>.
     * <p>
     * <b>Sample interpretations:</b> A Java object is an instance of its returned
     * {@link Object#getClass() class}.
     * <p>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#isMetaObject(Object)} must be implemented as well.
     *
     * @param instance the instance object to check.
     * @since 20.1
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static boolean isMetaInstance(@Host(Object.class) StaticObject receiver, @Host(Object.class) StaticObject instance, @InjectMeta Meta meta) {
        try {
            return UNCACHED.isMetaInstance(unwrap(receiver), unwrap(instance));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion MetaObject Messages

    // region Identity Messages

    /**
     * Returns <code>true</code> if two values represent the the identical value, else
     * <code>false</code>. Two values are identical if and only if they have specified identity
     * semantics in the target language and refer to the identical instance.
     * <p>
     * By default, an interop value does not support identical comparisons, and will return
     * <code>false</code> for any invocation of this method. Use
     * {@link InteropLibrary#hasIdentity(Object)} to find out whether a receiver supports identity
     * comparisons.
     * <p>
     * This method has the following properties:
     * <ul>
     * <li>It is <b>not</b> <i>reflexive</i>: for any value {@code x},
     * {@code lib.isIdentical(x, x, lib)} may return {@code false} if the object does not support
     * identity, else <code>true</code>. This method is reflexive if {@code x} supports identity. A
     * value supports identity if {@code lib.isIdentical(x, x, lib)} returns <code>true</code>. The
     * method {@link InteropLibrary#hasIdentity(Object)} may be used to document this intent
     * explicitly.
     * <li>It is <i>symmetric</i>: for any values {@code x} and {@code y},
     * {@code lib.isIdentical(x, y, yLib)} returns {@code true} if and only if
     * {@code lib.isIdentical(y, x, xLib)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any values {@code x}, {@code y}, and {@code z}, if
     * {@code lib.isIdentical(x, y, yLib)} returns {@code true} and
     * {@code lib.isIdentical(y, z, zLib)} returns {@code true}, then
     * {@code lib.isIdentical(x, z, zLib)} returns {@code true}.
     * <li>It is <i>consistent</i>: for any values {@code x} and {@code y}, multiple invocations of
     * {@code lib.isIdentical(x, y, yLib)} consistently returns {@code true} or consistently return
     * {@code false}.
     * </ul>
     * <p>
     * Note that the target language identical semantics typically does not map directly to interop
     * identical implementation. Instead target language identity is specified by the language
     * operation, may take multiple other rules into account and may only fallback to interop
     * identical for values without dedicated interop type. For example, in many languages
     * primitives like numbers or strings may be identical, in the target language sense, still
     * identity can only be exposed for objects and non-primitive values. Primitive values like
     * {@link Integer} can never be interop identical to other boxed language integers as this would
     * violate the symmetric property.
     * <p>
     * This method performs double dispatch by forwarding calls to
     * {@link InteropLibrary#isIdenticalOrUndefined(Object, Object)} with receiver and other value
     * first and then with reversed parameters if the result was {@link TriState#UNDEFINED
     * undefined}. This allows the receiver and the other value to negotiate identity semantics.
     * This method is supposed to be exported only if the receiver represents a wrapper that
     * forwards messages. In such a case the isIdentical message should be forwarded to the delegate
     * value. Otherwise, the {@link InteropLibrary#isIdenticalOrUndefined(Object, Object)} should be
     * exported instead.
     * <p>
     * This method must not cause any observable side-effects.
     *
     * For a full example please refer to the SLEqualNode of the SimpleLanguage example
     * implementation.
     *
     * @since 20.2
     */
    @Substitution
    public static boolean isIdentical(@Host(Object.class) StaticObject receiver, @Host(Object.class) StaticObject other) {
        return UNCACHED.isIdentical(unwrap(receiver), unwrap(other), UNCACHED);
    }

    /**
     * Returns an identity hash code for the receiver if it has
     * {@link InteropLibrary#hasIdentity(Object) identity}. If the receiver has no identity then an
     * {@link UnsupportedMessageException} is thrown. The identity hash code may be used by
     * languages to store foreign values with identity in an identity hash map.
     * <p>
     * <ul>
     * <li>Whenever it is invoked on the same object more than once during an execution of a guest
     * context, the identityHashCode method must consistently return the same integer. This integer
     * need not remain consistent from one execution context of a guest application to another
     * execution context of the same application.
     * <li>If two objects are the same according to the
     * {@link InteropLibrary#isIdentical(Object, Object)} message, then calling the identityHashCode
     * method on each of the two objects must produce the same integer result.
     * <li>As much as is reasonably practical, the identityHashCode message does return distinct
     * integers for objects that are not the same.
     * </ul>
     * This method must not cause any observable side-effects. If this method is implemented then
     * also {@link InteropLibrary#isIdenticalOrUndefined(Object, Object)} must be implemented.
     *
     * @see InteropLibrary#identityHashCode(Object)
     * @since 20.2
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static int identityHashCode(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta) {
        try {
            return UNCACHED.identityHashCode(unwrap(receiver));
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    // endregion Identity Messages


    // region Member Messages

    /**
     * Returns <code>true</code> if the receiver may have members. Therefore, at least one of
     * {@link #readMember(Object, String)}, {@link #writeMember(Object, String, Object)},
     * {@link #removeMember(Object, String)}, {@link #invokeMember(Object, String, Object...)} must
     * not throw {@link UnsupportedMessageException}. Members are structural elements of a class.
     * For example, a method or field is a member of a class. Invoking this message does not cause
     * any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #getMembers(Object, boolean)
     * @see #isMemberReadable(Object, String)
     * @see #isMemberModifiable(Object, String)
     * @see #isMemberInvocable(Object, String)
     * @see #isMemberInsertable(Object, String)
     * @see #isMemberRemovable(Object, String)
     * @see #readMember(Object, String)
     * @see #writeMember(Object, String, Object)
     * @see #removeMember(Object, String)
     * @see #invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Substitution
    public static boolean hasMembers(@Host(Object.class) StaticObject receiver) {
        return UNCACHED.hasMembers(unwrap(receiver));
    }

    /**
     * Returns an array of member name strings. The returned value must return <code>true</code> for
     * {@link #hasArrayElements(Object)} and every array element must be of type
     * {@link #isString(Object) string}. The member elements may also provide additional information
     * like {@link #getSourceLocation(Object) source location} in case of {@link #isScope(Object)
     * scope} variables, etc.
     * <p>
     * If the includeInternal argument is <code>true</code> then internal member names are returned
     * as well. Internal members are implementation specific and should not be exposed to guest
     * language application. An example of internal members are internal slots in ECMAScript.
     *
     * @see InteropLibrary#hasMembers(Object)
     * @since 19.0
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMembers(@Host(Object.class) StaticObject receiver, boolean includeInternal, @InjectMeta Meta meta) {
        try {
            Object value = UNCACHED.getMembers(unwrap(receiver), includeInternal);
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            return StaticObject.createForeign(meta.java_lang_Object, value, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Short-cut for {@link #getMembers(Object) getMembers(receiver, false)}. Invoking this message
     * does not cause any observable side-effects.
     *
     * @throws UnsupportedMessageException if and only if the receiver has no
     *             {@link #hasMembers(Object) members}.
     * @see #getMembers(Object, boolean)
     * @since 19.0
     */
    @Substitution
    @Throws(UnsupportedMessageException.class)
    public static @Host(Object.class) StaticObject getMembers(@Host(Object.class) StaticObject receiver, @InjectMeta Meta meta)  {
        return getMembers(receiver, false, meta);
    }

    /**
     * Returns <code>true</code> if a given member is {@link #readMember(Object, String) readable}.
     * This method may only return <code>true</code> if {@link #hasMembers(Object)} returns
     * <code>true</code> as well and {@link #isMemberInsertable(Object, String)} returns
     * <code>false</code>. Invoking this message does not cause any observable side-effects. Returns
     * <code>false</code> by default.
     *
     * @see #readMember(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberReadable(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberReadable(unwrap(receiver), hostMember);
    }

    /**
     * Reads the value of a given member. If the member is {@link #isMemberReadable(Object, String)
     * readable} and {@link #isMemberInvocable(Object, String) invocable} then the result of reading
     * the member is {@link #isExecutable(Object) executable} and is bound to this receiver. This
     * method must have not observable side-effects unless
     * {@link #hasMemberReadSideEffects(Object, String)} returns <code>true</code>.
     *
     * @throws UnsupportedMessageException if when the receiver does not support reading at all. An
     *             empty receiver with no readable members supports the read operation (even though
     *             there is nothing to read), therefore it throws {@link UnknownIdentifierException}
     *             for all arguments instead.
     * @throws UnknownIdentifierException if the given member is not
     *             {@link #isMemberReadable(Object, String) readable}, e.g. when the member with the
     *             given name does not exist.
     * @see #hasMemberReadSideEffects(Object, String)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnknownIdentifierException.class})
    public static @Host(Object.class) StaticObject readMember(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member, @InjectMeta Meta meta) {
        try {
            String hostMember = Meta.toHostStringStatic(member);
            Object value = UNCACHED.readMember(unwrap(receiver), hostMember);
            if (value instanceof StaticObject) {
                return (StaticObject) value;
            }
            return StaticObject.createForeign(meta.java_lang_Object, value, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given member is existing and
     * {@link #writeMember(Object, String, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    Substitution
    public static boolean isMemberModifiable(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberModifiable(unwrap(receiver), hostMember);
    }

    /**
     * Returns <code>true</code> if a given member is not existing and
     * {@link #writeMember(Object, String, Object) writable}. This method may only return
     * <code>true</code> if {@link #hasMembers(Object)} returns <code>true</code> as well and
     * {@link #isMemberExisting(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see #writeMember(Object, String, Object)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberInsertable(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberInsertable(unwrap(receiver), hostMember);
    }

    /**
     * Writes the value of a given member. Writing a member is allowed if is existing and
     * {@link InteropLibrary#isMemberModifiable(Object, String) modifiable}, or not existing and
     * {@link InteropLibrary#isMemberInsertable(Object, String) insertable}.
     *
     * This method must have not observable side-effects other than the changed member unless
     * {@link #hasMemberWriteSideEffects(Object, String) side-effects} are allowed.
     *
     * @see InteropLibrary#writeMember(Object, String, Object)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnknownIdentifierException.class, UnsupportedTypeException.class})
    public static void writeMember(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member, @Host(Object.class) StaticObject value,
                                   @InjectMeta Meta meta) {
        String hostMember = Meta.toHostStringStatic(value);
        try {
            if (receiver.isForeignObject()) {
                UNCACHED.writeMember(unwrap(receiver), hostMember, unwrap(value));
            } else {
                // Preserve the value type.
                UNCACHED.writeMember(receiver, hostMember, value);
            }
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given member is existing and removable. This method may only
     * return <code>true</code> if {@link InteropLibrary#hasMembers(Object)} returns <code>true</code> as well and
     * {@link InteropLibrary#isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberRemovable(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberRemovable(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberRemovable(unwrap(receiver), hostMember);
    }

    /**
     * Removes a member from the receiver object. Removing member is allowed if is
     * {@link InteropLibrary#isMemberRemovable(Object, String) removable}.
     *
     * This method does not have not observable side-effects other than the removed member.
     *
     * @see InteropLibrary#removeMember(Object, String)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, UnknownIdentifierException.class})
    public static void removeMember(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member, @InjectMeta Meta meta) {
        String hostMember = Meta.toHostStringStatic(member);
        try {
            UNCACHED.removeMember(unwrap(receiver), hostMember);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns <code>true</code> if a given member is invocable. This method may only return
     * <code>true</code> if {@link InteropLibrary#hasMembers(Object)} returns <code>true</code> as well and
     * {@link InteropLibrary#isMemberInsertable(Object, String)} returns <code>false</code>. Invoking this message
     * does not cause any observable side-effects. Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberInvocable(Object, String)
     * @see #invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberInvocable(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberInvocable(unwrap(receiver), hostMember);
    }

    /**
     * Invokes a member for a given receiver and arguments.
     *
     * @see InteropLibrary#invokeMember(Object, String, Object...)
     * @since 19.0
     */
    @Substitution
    @Throws({UnsupportedMessageException.class, ArityException.class, UnknownIdentifierException.class, UnsupportedTypeException.class})
    public static @Host(Object.class) StaticObject invokeMember(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member,
                                                                @Host(Object[].class) StaticObject arguments,
                                                                @InjectMeta Meta meta) {
        String hostMember = Meta.toHostStringStatic(member);
        try {
            Object[] args = null;
            Object result = null;

            if (receiver.isForeignObject()) {
                // Unwrap arguments.
                args = new Object[arguments.length()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = unwrap(meta.getInterpreterToVM().getArrayObject(i, arguments));
                }
                result = UNCACHED.invokeMember(unwrap(receiver), hostMember, args);
            } else {
                // Preserve argument types.
                if (arguments.isEspressoObject()) {
                    // Avoid copying, use the underlying array.
                    args = arguments.unwrap();
                } else {
                    args = new Object[arguments.length()];
                    for (int i = 0; i < args.length; i++) {
                        args[i] = meta.getInterpreterToVM().getArrayObject(i, arguments);
                    }
                }
                result = UNCACHED.invokeMember(receiver, hostMember, args);
            }

            if (result instanceof StaticObject) {
                return (StaticObject) result;
            }
            return StaticObject.createForeign(meta.java_lang_Object, result, UNCACHED);
        } catch (InteropException e) {
            throw throwInteropException(e, meta);
        }
    }

    /**
     * Returns true if a member is internal. Internal members are not enumerated by
     * {@link InteropLibrary#getMembers(Object, boolean)} by default. Internal members are only relevant to guest
     * language implementations and tools, but not to guest applications or embedders. An example of
     * internal members are internal slots in ECMAScript. Invoking this message does not cause any
     * observable side-effects. Returns <code>false</code> by default.
     *
     * @see InteropLibrary#isMemberInternal(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean isMemberInternal(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.isMemberInternal(unwrap(receiver), hostMember);
    }

    /**
     * Returns <code>true</code> if reading a member may cause a side-effect. Invoking this message
     * does not cause any observable side-effects. A member read does not cause any side-effects by
     * default.
     * <p>
     * For instance in JavaScript a property read may have side-effects if the property has a getter
     * function.
     *
     * @see InteropLibrary#hasMemberReadSideEffects(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean hasMemberReadSideEffects(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.hasMemberReadSideEffects(unwrap(receiver), hostMember);
    }

    /**
     * Returns <code>true</code> if writing a member may cause a side-effect, besides the write
     * operation of the member. Invoking this message does not cause any observable side-effects. A
     * member write does not cause any side-effects by default.
     * <p>
     * For instance in JavaScript a property write may have side-effects if the property has a
     * setter function.
     *
     * @see InteropLibrary#hasMemberWriteSideEffects(Object, String)
     * @since 19.0
     */
    @Substitution
    public static boolean hasMemberWriteSideEffects(@Host(Object.class) StaticObject receiver, @Host(String.class) StaticObject member) {
        String hostMember = Meta.toHostStringStatic(member);
        return UNCACHED.hasMemberWriteSideEffects(unwrap(receiver), hostMember);
    }

    // endregion Member Messages

    private static Object unwrap(StaticObject receiver) {
        return receiver.isForeignObject() ? receiver.rawForeignObject() : receiver;
    }

    private static StaticObject wrapForeignException(Throwable throwable, Meta meta) {
        assert UNCACHED.isException(throwable);
        assert throwable instanceof AbstractTruffleException;
        return StaticObject.createForeign(meta.polyglot.ForeignException, throwable, UNCACHED);
    }

    @TruffleBoundary
    private static RuntimeException throwInteropException(InteropException e, Meta meta) {
        if (e instanceof UnsupportedMessageException) {
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                            // UnsupportedMessageException.create()
                            ? (StaticObject) meta.polyglot.UnsupportedMessageException_create.invokeDirect(null)
                            // UnsupportedMessageException.create(Throwable cause)
                            : (StaticObject) meta.polyglot.UnsupportedMessageException_create_Throwable.invokeDirect(null, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        if (e instanceof UnknownIdentifierException) {
            StaticObject unknownIdentifier = meta.toGuestString(((UnknownIdentifierException) e).getUnknownIdentifier());
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                            // UnknownIdentifierException.create(String unknownIdentifier)
                            ? (StaticObject) meta.polyglot.UnknownIdentifierException_create_String.invokeDirect(null, unknownIdentifier)
                            // UnknownIdentifierException.create(String unknownIdentifier, Throwable
                            // cause)
                            : (StaticObject) meta.polyglot.UnknownIdentifierException_create_String_Throwable.invokeDirect(null, unknownIdentifier, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        if (e instanceof ArityException) {
            int expectedArity = ((ArityException) e).getExpectedArity();
            int actualArity = ((ArityException) e).getActualArity();
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                            // ArityException.create(int expectedArity, int actualArity)
                            ? (StaticObject) meta.polyglot.UnknownIdentifierException_create_String.invokeDirect(null, expectedArity, actualArity)
                            // ArityException.create(int expectedArity, int actualArity, Throwable
                            // cause)
                            : (StaticObject) meta.polyglot.UnknownIdentifierException_create_String_Throwable.invokeDirect(null, expectedArity, actualArity, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        if (e instanceof UnsupportedTypeException) {
            Object[] hostValues = ((UnsupportedTypeException) e).getSuppliedValues();
            // Transform suppliedValues[] into a guest Object[].
            StaticObject[] backingArray = new StaticObject[hostValues.length];
            for (int i = 0; i < backingArray.length; i++) {
                Object value = hostValues[i];
                if (value instanceof StaticObject) {
                    backingArray[i] = (StaticObject) value; // no need to re-type
                } else {
                    // TODO(peterssen): Wrap with precise types.
                    backingArray[i] = StaticObject.createForeign(meta.java_lang_Object, value, UNCACHED);
                }
            }
            StaticObject suppliedValues = StaticObject.wrap(backingArray, meta);
            StaticObject hint = meta.toGuestString(e.getMessage());
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                            // UnsupportedTypeException.create(Object[] suppliedValues, String hint)
                            ? (StaticObject) meta.polyglot.UnsupportedTypeException_create_Object_array_String.invokeDirect(null, suppliedValues, hint)
                            // UnsupportedTypeException.create(Object[] suppliedValues, String hint,
                            // Throwable cause)
                            : (StaticObject) meta.polyglot.UnsupportedTypeException_create_Object_array_String_Throwable.invokeDirect(null, suppliedValues, hint, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        if (e instanceof InvalidArrayIndexException) {
            long invalidIndex = ((InvalidArrayIndexException) e).getInvalidIndex();
            Throwable cause = e.getCause();
            assert cause == null || cause instanceof AbstractTruffleException;
            StaticObject exception = (cause == null)
                            // InvalidArrayIndexException.create(long invalidIndex)
                            ? (StaticObject) meta.polyglot.InvalidArrayIndexException_create_long.invokeDirect(null, invalidIndex)
                            // InvalidArrayIndexException.create(long invalidIndex, Throwable cause)
                            : (StaticObject) meta.polyglot.InvalidArrayIndexException_create_long_Throwable.invokeDirect(null, invalidIndex, wrapForeignException(cause, meta));
            throw EspressoException.wrap(exception);
        }

        CompilerDirectives.transferToInterpreter();
        throw EspressoError.unexpected("Unexpected interop exception: ", e);
    }
}
