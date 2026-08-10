---
title: Adopting a functional paradigm with Jupyter Notebooks
date: 2021-04-19
description: Adopting a functional paradigm with Jupyter Notebooks
author: Valentin Mouret
---

In case you don't know, a [Jupyter](https://jupyter.org/) Notebook is a webpage running along with some REPL (Read Eval Print Loop) — most often Python — where you can interact with the program and write a document as you go. It supports Markdown and LaTex-like mathematical annotations, which help to build good-looking documents. Jupyter Notebook is a tool widely used by data analysts, engineers, scientists, and others. It is popular because Jupyter makes development interactive: You can run some code, get immediate feedback, and easily turn it into a shareable document.

However, it's hard to strike a balance between lightweight and organized code. Notebooks tend to be a place of chaos and random behavior, but don't give up! It's possible to do better.

## Limits of most notebooks

From my experience with Notebooks, I found three main limits: They are error-prone. They are hard to follow. Taking the code to production is “hell”.

### 1. They are error-prone

Commonly, most of the variables and functions are kept in the main scope. Like the example below:

```python
data = [1, 2, 3]

def do_ai(data):
    total = sum(data)
    count = len(data)
    return total / count

do_ai(data)
# 2.0

# Stuff happens
...

data = "oh no"
characters = len(data)
characters
# 5
```

What happens when we run cell [8] again? It would fail because the variable data has changed; it was mutated.

It's fine most of the time, but it can lead to subtle bugs or incomprehension. First: what does data mean in this context? It's the function's argument, but it was also defined in the main scope.

I read many Notebooks where the data is defined in the main scope and it's accessed or mutated from inside functions without it being declared as an argument, like the example below:

```python
import pandas as pd
import random

data = pd.DataFrame({
    "signal": [random.gauss(0.0, 1.0) for _ in range(100)],
})

def do_ai():
    data["signal_squared"] = data["signal"] ** 2

do_ai()
data
```

But what if `data` changes? What if it becomes a string?

This can lead to subtle errors that might manifest *after* you have shared your promising results with your team. I hate when that happens.

Also, as you rewrite or delete cells, you might lose some critical parts of your pipeline. It can be head-scratching the next day when you run your cells with a fresh REPL and find pieces are missing.

### 2. They are hard to follow

While it can be clear as you are writing it, it may be obscure for other readers. Don't be egotistical because the «other» can be you tomorrow or in three months when you really want to conduct this analysis again.

**The reason is simple: it's cognitive load.**

As you are writing it, it stays warm in your head. You already loaded it in your brain's RAM and you don't have issues working with it. So *workflows* like launching cell one, then cell five, then cell one again work with warm memories but are atrocious when you completely forgot what you were doing.

New readers will have to «load». If it's a big spaghetti, you ask them to load a lot at once, which consumes a lot of energy and attention. And attention is limited.

Here is an extract of a *hot* [Notebook](https://www.kaggle.com/ldfreeman3/a-data-science-framework-to-achieve-99-accuracy) I found on Kaggle:

```python
import pandas as pd

data_raw = pd.read_csv('../input/train.csv')
data_val = pd.read_csv('../input/test.csv')

data1 = data_raw.copy(deep=True)
data_cleaner = [data1, data_val]

for dataset in data_cleaner:
    # complete missing age with median
    dataset['Age'].fillna(dataset['Age'].median(), inplace=True)

    # complete embedded with mode
    dataset['Embarked'].fillna(dataset['Embarked'].mode()[0], inplace=True)

    # complete missing fare with median
    dataset['Fare'].fillna(dataset['Fare'].median(), inplace=True)

    # delete the cabin feature/column and others previously stated to exclude in train dataset
    drop_column = ['PassengerId', 'Cabin', 'Ticket']
    data1.drop(drop_column, axis=1, inplace=True)

    # quick and dirty code split title from name
    dataset['Title'] = dataset['Name'].str.split(", ", expand=True)[1].str.split('.', expand=True)[0]

    # continuous variable bins: qcut vs cut
    data1['FareBin'] = pd.qcut(data1['Fare'], 4)
    data1['AgeBin'] = pd.cut(data1['Age'].astype(int), 5)

# cleanup rare title names
stat_min = 10
title_names = data1['Title'].value_counts() < stat_min

# apply and lambda functions to find and replace with fewer lines of code
data1['Title'] = data1['Title'].apply(
    lambda x: 'Misc' if title_names.loc[x] == True else x
)
```

It's only an extract; it goes on for a while. When writing things like that, you ask your readers to remember the state of a mutable object, which requires a high cognitive load (it's a mess).

Plus, if it's stateful, who knows what will happen if you relaunch it (provided the reading doesn't happen again)?

### 3. Going to production is “hell”

At the end of the day, you will want to turn your efforts into something useful. That could be a report if it's an analysis or a machine learning pipeline if you were training a model. Most of the time, it's almost equivalent to starting over.

You cannot take the above snippet and go to town. Production has different requirements. You want tests, and you want to expose some variables so that you can — for instance — conduct the same analysis on a different day.

If everything is stateful and in the main scope, you will have a hard time doing that.

Those are simple, seemingly harmless things that I admit I have been doing for a while. But the costs of this are high. Hopefully, we can apply simple rules of functional programming to solve all of that and more!

## Functional programming tips

### Paradigm

Functional programming is a programming paradigm in which you dissociate the code and the data. You deal with (mostly) pure functions, meaning functions that don't modify their inputs nor interact with the outside world.

This may appear odd, but you will see it has simple implications, especially in Python, which offers flexibility.

If you take our `do_machine_learning` function from earlier, it's pure. It doesn't interact with the outside world, nor does it modify its inputs. `do_ai` on the other hand isn't pure because it modifies a variable from the main scope.

If a function is pure, calling it with some input will always return the same output. That reduces cognitive load. I don't have to remember much besides what the function is doing.

### Application

Applying functional programming to our context is simple. Decompose things into functions. Remember this Jupyter Notebook from Kaggle I mentioned earlier? The blocks below those comments could be almost extracted one-to-one into functions with the comment as the function's name. It would improve clarity because the execution would be bounded. It would be clear what the inputs and computations are.

Additionally, by using pandas functions without `inplace=True` (which — by the way — [will be deprecated](https://github.com/pandas-dev/pandas/issues/16529)) we get pure functions:

```python
data_raw = pd.read_csv('../input/train.csv')
data_val = pd.read_csv('../input/test.csv')

data1 = data_raw.copy(deep=True)
data_cleaner = [data1, data_val]

def impute(data):
    return data.assign(
        Age=data['Age'].fillna(data['Age'].median()),
        Embarked=data['Embarked'].fillna(data['Embarked'].mode()[0]),
        Fare=data['Fare'].fillna(data['Fare'].median())
    )

def drop_columns(data):
    to_drop = ['PassengerId', 'Cabin', 'Ticket']
    return data.drop(to_drop, axis=1)

def split_title(data):
    return data.assign(
        Title=data['Name'].str.split(", ", expand=True)[1].str.split('.', expand=True)[0]
    )

def bin_continuous_features(data):
    return data.assign(
        FareBin=pd.cut(data['Fare'], 4),
        AgeBin=pd.cut(data['Age'].astype(int), 5)
    )

def cleanup_rare_titles(data):
    stat_min = 10
    title_names = data['Title'].value_counts() < stat_min
    return data.assign(
        Title=data['Title'].apply(
            lambda x: 'Misc' if title_names.loc[x] == True else x
        )
    )
```

Each function does only one thing. The input is clearly stated, and it's not mutated. We return a transformation of the input.

Now, we could be getting the output of it all by composing those functions, either like a savage:

```python
encode_categorical_data(
    cleanup_rare_titles(
        bin_continuous_features(
            split_title(
                drop_columns(
                    impute(
                        data_1
                    )
                )
            )
        )
    )
)
```

Or using Pandas [piping](https://pandas.pydata.org/pandas-docs/stable/reference/api/pandas.DataFrame.pipe.html) function:

```python
(
    data1.pipe(impute)
        .pipe(drop_columns)
        .pipe(split_title)
        .pipe(bin_continuous_features)
        .pipe(cleanup_rare_titles)
        .pipe(encode_categorical_data)
)
```

Or with a more general approach using reduce:

```python
from functools import reduce

funcs = (
    impute,
    drop_columns,
    split_title,
    bin_continuous_features,
    cleanup_rare_titles,
    encode_categorical_data,
)

def apply(result, f):
    return f(result)

reduce(apply, funcs, data_1)
```

## Conclusion

By adopting a functional paradigm in Jupyter Notebook, you can break your code down into functions.

It's less error-prone because each step does exactly one thing. If you have a bug in a function, fix it, rerun the cell, and things are fine.

It's easier to follow because your steps are clearly identified, named, and state their inputs and outputs.

Also, they are easier to test. You can test each one independently without messing around with a big spaghetti in the main scope. So, going to production is easier.

It came at the cost of little overhead (it's only functions!) – and, guess what: it has more impact than just cutting the time you spend working on your Notebooks, reading your colleagues' work, and taking all of that to production.

Since you broke things down into independent steps, they can easily be reused in other projects to do the same thing. You can test them once and reuse them, and compose them all across your projects! The more you do, the less you have to do. It compounds.
